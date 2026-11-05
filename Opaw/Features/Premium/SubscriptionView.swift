import StoreKit
import SwiftUI

/// Settings: Subscription (5f): the trial countdown or plan, renewal and billing, and Manage subscription.
struct SubscriptionView: View {
    /// True in the open display's right pane (6o): a left-aligned title, fewer details, Restore beside Manage.
    var inPane = false
    @Environment(PremiumStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var showManage = false
    @State private var showPaywall = false

    var body: some View {
        VStack(spacing: 0) {
            if inPane {
                PaneHeader(title: "Subscription")
            } else {
                ScreenHeader(title: "Subscription") {
                    Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Back")
                } trailing: {
                    Color.clear.frame(width: 54, height: 54)
                }
            }
            ScrollView {
                VStack(spacing: 0) {
                    if let status = store.status {
                        planCard(status)
                            .padding(.top, inPane ? 12 : 18)
                        details(status)
                            .padding(.top, 12)
                        if !inPane {
                        Text(footnote(status))
                            .font(.opaw(14, .medium))
                            .foregroundStyle(Color.opawMuted)
                            .lineSpacing(3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 8)
                            .padding(.top, 14)
                        }
                    } else {
                        freeCard
                            .padding(.top, inPane ? 12 : 18)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, inPane ? 90 : 160)
            }
            .scrollIndicators(.hidden)
            .padding(.horizontal, inPane ? -20 : 0)
        }
        .overlay(alignment: .bottom) {
            if inPane {
                paneButtons
            } else {
            VStack(spacing: 14) {
                if store.isPremium {
                    Button("Manage subscription") { showManage = true }
                        .buttonStyle(BlackPillButtonStyle(height: 62))
                } else {
                    Button("Try Opaw Premium") { showPaywall = true }
                        .buttonStyle(BlackPillButtonStyle(height: 62))
                }
                Button("Restore purchases") { Task { await store.restore() } }
                    .font(.opaw(15, .bold))
                    .foregroundStyle(Color.opawInk)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
            }
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
        .hidesFloatingTabBar()
        .manageSubscriptionsSheet(isPresented: $showManage)
        .onChange(of: showManage) { _, open in
            if !open { Task { await store.refreshStatus() } }
        }
        .premiumPaywall(isPresented: $showPaywall)
        .alert("Opaw Premium", isPresented: Binding(get: { store.errorMessage != nil }, set: { if !$0 { store.errorMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(store.errorMessage ?? "")
        }
    }

    /// Restore, then Manage subscription at twice the width (6o).
    private var paneButtons: some View {
        GeometryReader { proxy in
            let restoreWidth = (proxy.size.width - 10) / 3
            HStack(spacing: 10) {
                Button("Restore") { Task { await store.restore() } }
                    .buttonStyle(BlackPillButtonStyle(height: 62, inverted: true, fontSize: 16))
                    .frame(width: restoreWidth)
                if store.isPremium {
                    Button("Manage subscription") { showManage = true }
                        .buttonStyle(BlackPillButtonStyle(height: 62, fontSize: 16))
                } else {
                    Button("Try Opaw Premium") { showPaywall = true }
                        .buttonStyle(BlackPillButtonStyle(height: 62, fontSize: 16))
                }
            }
        }
        .frame(height: 62)
    }

    // MARK: Cards

    private func planCard(_ status: PremiumStore.Status) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Opaw Premium")
                    .font(.opaw(15, .heavy))
                Spacer()
                Text(status.isTrial ? "Free trial" : (status.willRenew ? "Active" : "Ending"))
                    .font(.opaw(12, .heavy))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color(hex: 0x111111), in: Capsule())
            }
            Text(headline(status))
                .font(.opaw(inPane ? 40 : 44, .black))
                .tracking(inPane ? -0.8 : 0)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .padding(.top, inPane ? 12 : 14)
            if status.isTrial, let end = status.expirationDate {
                let total = max(1, Calendar.current.dateComponents([.day], from: status.purchaseDate, to: end).day ?? 7)
                let used = min(total, max(0, total - daysLeft(until: end)))
                HStack(spacing: 5) {
                    ForEach(0..<total, id: \.self) { index in
                        Capsule()
                            .fill(index < used ? Color(hex: 0x111111) : Color.opawTrack)
                            .frame(height: 8)
                    }
                }
                .padding(.top, inPane ? 14 : 16)
            }
            if !inPane {
            HStack {
                Text("Started \(status.purchaseDate.formatted(.dateTime.month(.abbreviated).day()))")
                Spacer()
                if let end = status.expirationDate {
                    Text("\(status.isTrial || !status.willRenew ? "Ends" : "Renews") \(end.formatted(.dateTime.month(.abbreviated).day()))")
                }
            }
            .font(.opaw(12, .semibold))
            .foregroundStyle(Color.opawFaint)
            .padding(.top, 8)
            }
        }
        .foregroundStyle(Color.opawInk)
        .padding(.horizontal, 20)
        .padding(.vertical, inPane ? 18 : 20)
        .whiteCard()
    }

    private func details(_ status: PremiumStore.Status) -> some View {
        VStack(spacing: 0) {
            detailRow("Plan", planText(status), first: true)
            if let end = status.expirationDate {
                detailRow(status.willRenew ? "Renews" : "Ends", end.formatted(.dateTime.month(.abbreviated).day().year()))
            }
            if let reminder = store.trialReminderDate {
                detailRow("Reminder", reminder.formatted(.dateTime.month(.abbreviated).day()) + (inPane ? "" : ", by notification"))
            }
            if !inPane {
                detailRow("Billed through", "App Store")
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, inPane ? 2 : 4)
        .whiteCard()
    }

    private func detailRow(_ label: String, _ value: String, first: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.opaw(15, .semibold))
                .foregroundStyle(Color.opawMuted)
            Spacer()
            Text(value)
                .font(.opaw(15, .heavy))
                .foregroundStyle(Color.opawInk)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, inPane ? 12 : 14)
        .overlay(alignment: .top) {
            if !first { Rectangle().fill(Color.opawHairline).frame(height: 1) }
        }
    }

    private var freeCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Opaw")
                    .font(.opaw(15, .heavy))
                Spacer()
                Text("Free")
                    .font(.opaw(12, .heavy))
                    .foregroundStyle(Color(hex: 0x888888))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.opawChip, in: Capsule())
            }
            Text("Free plan")
                .font(.opaw(44, .black))
                .padding(.top, 14)
            Text("One pet, with care logs, walks and memories. Premium adds more pets, unlimited reminders, full history, widgets and backup.")
                .font(.opaw(14, .medium))
                .foregroundStyle(Color.opawMuted)
                .lineSpacing(3)
                .padding(.top, 10)
        }
        .foregroundStyle(Color.opawInk)
        .padding(20)
        .whiteCard()
    }

    // MARK: Text

    private func daysLeft(until end: Date) -> Int {
        let calendar = Calendar.current
        return max(0, calendar.dateComponents([.day], from: calendar.startOfDay(for: Date()), to: calendar.startOfDay(for: end)).day ?? 0)
    }

    private func headline(_ status: PremiumStore.Status) -> String {
        if status.isTrial, let end = status.expirationDate {
            let days = daysLeft(until: end)
            return days == 1 ? "1 day left" : "\(days) days left"
        }
        return status.plan.title
    }

    private func planText(_ status: PremiumStore.Status) -> String {
        let price = store.products[status.productID]?.displayPrice
        return [status.plan.title, price].compactMap { $0 }.joined(separator: " · ")
    }

    private func footnote(_ status: PremiumStore.Status) -> String {
        guard let end = status.expirationDate else { return "" }
        let day = end.formatted(.dateTime.month(.abbreviated).day())
        if status.isTrial {
            return "Cancel at least 24 hours before \(day) and you won’t be charged."
        }
        return status.willRenew
            ? "Cancel at least 24 hours before \(day) to stop it renewing."
            : "Premium stays on until \(day)."
    }
}

/// Locked: adding a second pet (5d). Free covers one pet.
struct PetLimitSheet: View {
    let dog: Dog?
    let onTryPremium: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetGrabber()
                .frame(maxWidth: .infinity)
            HStack(spacing: -14) {
                MiniFace(data: dog?.cutoutData ?? dog?.photoData, size: 60, rim: 4, fill: Color.opawTrack)
                    .shadow(color: .black.opacity(0.08), radius: 8, y: 6)
                lockFace
                lockFace
            }
            .padding(.top, 20)
            Text("Room for more pets")
                .font(.opaw(26, .black))
                .foregroundStyle(Color(hex: 0x111111))
                .padding(.top, 16)
            Text("Free covers one pet. Premium adds unlimited pets, each with their own avatar.")
                .font(.opaw(15, .medium))
                .foregroundStyle(Color(hex: 0x999999))
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 6)
            Spacer(minLength: 20)
            Button("Try Premium free for 7 days") {
                dismiss()
                // Let the sheet finish closing before the paywall covers the screen.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { onTryPremium() }
            }
            .buttonStyle(BlackPillButtonStyle(height: 62))
            Button("Not now") { dismiss() }
                .font(.opaw(15, .bold))
                .foregroundStyle(Color(hex: 0x999999))
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .padding(.top, 2)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 4)
        .presentationDetents([.height(420)])
        .presentationCornerRadius(40)
        .presentationDragIndicator(.hidden)
        .presentationBackground(Color.white)
    }

    private var lockFace: some View {
        Icon(.lock, size: 20)
            .foregroundStyle(Color(hex: 0x999999))
            .frame(width: 52, height: 52)
            .background(Color(hex: 0xF1F1F2), in: Circle())
            .overlay(Circle().strokeBorder(Color.white, lineWidth: 4).frame(width: 60, height: 60))
            .frame(width: 60, height: 60)
    }
}
