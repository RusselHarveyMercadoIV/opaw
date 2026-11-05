import Foundation
import StoreKit
import UserNotifications

/// Opaw Premium: loads the plans, buys and restores them, and keeps track of whether Premium is on.
///
/// Prices and the free trial come from the App Store. While testing in Xcode they come from
/// Config/Opaw.storekit, which the Opaw scheme uses for every run.
@MainActor
@Observable
final class PremiumStore {
    enum Plan: String, CaseIterable {
        case yearly, monthly

        var productID: String { "app.opaw.premium.\(rawValue)" }
        var title: String { self == .yearly ? "Yearly" : "Monthly" }
    }

    /// The yearly plan with 50% off the first year, shown once after closing the paywall.
    static let offerProductID = "app.opaw.premium.yearly.offer"
    static var allProductIDs: [String] { Plan.allCases.map(\.productID) + [offerProductID] }

    /// What the person has right now.
    struct Status: Equatable {
        var productID: String
        var isTrial: Bool
        var purchaseDate: Date
        var expirationDate: Date?
        var willRenew: Bool

        var plan: Plan { productID == Plan.monthly.productID ? .monthly : .yearly }
    }

    private(set) var products: [String: Product] = [:]
    private(set) var status: Status?
    private(set) var isPurchasing = false
    private(set) var isLoaded = false
    var errorMessage: String?

    var isPremium: Bool { status != nil }

    private var updates: Task<Void, Never>?

    init() {
        updates = Task { [weak self] in
            for await update in Transaction.updates {
                if case .verified(let transaction) = update {
                    await transaction.finish()
                }
                await self?.refreshStatus()
            }
        }
        Task { await load() }
    }

    func product(_ plan: Plan) -> Product? { products[plan.productID] }
    var offerProduct: Product? { products[Self.offerProductID] }

    func load() async {
        if let loaded = try? await Product.products(for: Self.allProductIDs) {
            products = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0) })
        }
        await refreshStatus()
        isLoaded = true
    }

    /// True when the person can still get the free trial (they haven't had one before).
    func isEligibleForTrial(_ plan: Plan) async -> Bool {
        guard let subscription = product(plan)?.subscription, subscription.introductoryOffer != nil else { return false }
        return await subscription.isEligibleForIntroOffer
    }

    /// Buys a plan. Returns true when Premium is on afterwards.
    @discardableResult
    func purchase(_ product: Product) async -> Bool {
        isPurchasing = true
        defer { isPurchasing = false }
        do {
            switch try await product.purchase() {
            case .success(let result):
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    await refreshStatus()
                    if Self.isIntroductory(transaction), product.subscription?.introductoryOffer?.paymentMode == .freeTrial {
                        await scheduleTrialReminder()
                    }
                    return isPremium
                }
                errorMessage = "The App Store couldn't confirm that purchase."
            case .pending:
                errorMessage = "Your purchase is waiting for approval."
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        return false
    }

    func restore() async {
        isPurchasing = true
        defer { isPurchasing = false }
        do {
            try await AppStore.sync()
        } catch {
            errorMessage = error.localizedDescription
        }
        await refreshStatus()
        if !isPremium && errorMessage == nil {
            errorMessage = "There's no Opaw Premium purchase on this Apple Account."
        }
    }

    func refreshStatus() async {
        var best: Status?
        for await entitlement in Transaction.currentEntitlements {
            guard case .verified(let transaction) = entitlement,
                  Self.allProductIDs.contains(transaction.productID),
                  transaction.revocationDate == nil else { continue }
            var willRenew = true
            if let product = products[transaction.productID],
               let statuses = try? await product.subscription?.status,
               let current = statuses.first(where: {
                   if case .verified(let renewal) = $0.renewalInfo { return renewal.originalTransactionID == transaction.originalID }
                   return false
               }),
               case .verified(let renewal) = current.renewalInfo {
                willRenew = renewal.willAutoRenew
            }
            let candidate = Status(
                productID: transaction.productID,
                isTrial: Self.isIntroductory(transaction) && products[transaction.productID]?.subscription?.introductoryOffer?.paymentMode == .freeTrial,
                purchaseDate: transaction.purchaseDate,
                expirationDate: transaction.expirationDate,
                willRenew: willRenew
            )
            if best == nil || (candidate.expirationDate ?? .distantFuture) > (best?.expirationDate ?? .distantFuture) {
                best = candidate
            }
        }
        status = best
    }

    private static func isIntroductory(_ transaction: Transaction) -> Bool {
        if #available(iOS 17.2, *) {
            return transaction.offer?.type == .introductory
        }
        return transaction.offerType == .introductory
    }

    /// Two days before the trial ends, a notification says billing is about to start.
    var trialReminderDate: Date? {
        guard let status, status.isTrial, let end = status.expirationDate else { return nil }
        return Calendar.current.date(byAdding: .day, value: -2, to: end)
    }

    private func scheduleTrialReminder() async {
        guard let date = trialReminderDate, date > Date() else { return }
        let content = UNMutableNotificationContent()
        content.title = "Your Opaw Premium trial ends in 2 days"
        content.body = "Cancel in Settings before then if you don't want to be charged."
        content.sound = .default
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let request = UNNotificationRequest(
            identifier: "premium.trial.reminder",
            content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
        )
        try? await UNUserNotificationCenter.current().add(request)
    }
}

extension Product {
    /// The price for one month of a yearly plan, such as "$2.50".
    var monthlyEquivalent: String? {
        guard subscription?.subscriptionPeriod.unit == .year else { return nil }
        return (price / 12).formatted(priceFormatStyle.precision(.fractionLength(2)))
    }
}
