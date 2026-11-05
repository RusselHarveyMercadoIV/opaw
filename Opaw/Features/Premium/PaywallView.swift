import StoreKit
import SwiftData
import SwiftUI

/// Trial paywall (5a light, 5b dark): the pet, what Premium adds, yearly or monthly, and the trial timeline.
struct PaywallView: View {
    let petName: String
    let petImage: UIImage?
    /// Called with true when Premium is now on, false when closed without buying.
    let onClose: (Bool) -> Void

    @Environment(PremiumStore.self) private var store
    @Environment(\.colorScheme) private var scheme
    @State private var plan: PremiumStore.Plan = .yearly
    @State private var trialEligible = true

    private var dark: Bool { scheme == .dark }
    private var ink: Color { dark ? .white : Color(hex: 0x111111) }
    private var muted: Color { dark ? Color(hex: 0x8E8E93) : Color(hex: 0x999999) }
    private var surface: Color { dark ? Color(hex: 0x1C1C1F) : .white }
    private var background: Color { dark ? Color(hex: 0x0D0D0F) : Color(hex: 0xF4F4F5) }

    var body: some View {
        VStack(spacing: 0) {
            PetSticker(image: petImage, edge: 5, motion: .bob, duration: 3.2)
                .frame(width: 112, height: 126)
                .frame(height: 132)
                .padding(.top, 4)
            VStack(spacing: 4) {
                Text("Opaw Premium")
                    .font(.opaw(13, .heavy))
                    .foregroundStyle(muted)
                Text("Give \(petName) the best care")
                    .font(.opaw(30, .black))
                    .foregroundStyle(ink)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
            }
            .padding(.top, 8)
            .padding(.horizontal, 20)

            features
                .padding(.horizontal, 24)
                .padding(.top, 16)

            VStack(spacing: 8) {
                planRow(.yearly)
                planRow(.monthly)
            }
            .padding(.horizontal, 20)
            .padding(.top, 18)

            Button {
                Task { await buy() }
            } label: {
                if store.isPurchasing {
                    ProgressView().tint(dark ? Color(hex: 0x111111) : .white)
                } else {
                    Text(trialEligible ? "Start 7-day free trial" : "Continue")
                }
            }
            .buttonStyle(BlackPillButtonStyle(height: 62, inverted: dark))
            .disabled(store.product(plan) == nil || store.isPurchasing)
            .padding(.horizontal, 20)
            .padding(.top, 16)

            Text(caption)
                .font(.opaw(14, .semibold))
                .foregroundStyle(muted)
                .padding(.top, 10)

            if trialEligible {
                timeline
                    .padding(.horizontal, 36)
                    .padding(.top, 14)
            }

            Spacer(minLength: 12)

            HStack(spacing: 18) {
                Button("Restore purchases") {
                    Task {
                        await store.restore()
                        if store.isPremium { onClose(true) }
                    }
                }
                Link("Terms", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
                Link("Privacy", destination: URL(string: "https://github.com/RusselHarveyMercadoIV/opaw/blob/main/docs/PRIVACY.md")!)
            }
            .font(.opaw(13, .bold))
            .foregroundStyle(muted)
            .padding(.bottom, 12)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(alignment: .topLeading) {
            Button { onClose(false) } label: {
                Icon(.close, size: 22)
                    .foregroundStyle(ink)
                    .frame(width: 54, height: 54)
                    .background(surface, in: Circle())
                    .shadow(color: .black.opacity(dark ? 0 : 0.07), radius: 13, y: 10)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Close")
            .padding(.leading, 20)
            .padding(.top, 6)
        }
        .background(background.ignoresSafeArea())
        .task(id: plan) { trialEligible = await store.isEligibleForTrial(plan) || store.products.isEmpty }
        .alert("Opaw Premium", isPresented: Binding(get: { store.errorMessage != nil }, set: { if !$0 { store.errorMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(store.errorMessage ?? "")
        }
    }

    // MARK: Pieces

    private var features: some View {
        VStack(alignment: .leading, spacing: 8) {
            feature(.pets, "Multiple pets, each with an avatar")
            feature(.bell, "Unlimited reminders")
            feature(.chart, "Full history and stats")
            feature(.activity, "Walks and memories")
            feature(.users, "Family sharing, widgets and backup")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func feature(_ icon: DesignIcon, _ text: String) -> some View {
        HStack(spacing: 14) {
            Group {
                if icon == .activity {
                    Icon(.activity, size: 17)
                } else {
                    Icon(icon, size: 17, weight: 2.4)
                }
            }
            .foregroundStyle(ink)
            .frame(width: 34, height: 34)
            .background(surface, in: Circle())
            .shadow(color: .black.opacity(dark ? 0 : 0.05), radius: 7, y: 6)
            Text(text)
                .font(.opaw(16, .bold))
                .foregroundStyle(ink)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }

    private func planRow(_ option: PremiumStore.Plan) -> some View {
        let selected = plan == option
        let product = store.product(option)
        return Button {
            Haptics.select()
            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { plan = option }
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    if selected {
                        Circle().fill(ink)
                        Icon(.check, size: 12)
                            .foregroundStyle(dark ? Color(hex: 0x111111) : .white)
                    } else {
                        Circle().strokeBorder(dark ? Color(hex: 0x48484D) : Color(hex: 0xD4D4D8), lineWidth: 2)
                    }
                }
                .frame(width: 22, height: 22)
                HStack(spacing: 8) {
                    Text(option.title)
                        .font(.opaw(17, .heavy))
                    if option == .yearly, let saving {
                        Text("Save \(saving)%")
                            .font(.opaw(12, .heavy))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(Color.opawAlert, in: Capsule())
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 0) {
                    Text(priceLabel(option, product: product))
                        .font(.opaw(17, .black))
                    if option == .yearly, let monthly = product?.monthlyEquivalent {
                        Text("\(monthly)/mo")
                            .font(.opaw(12, .semibold))
                            .foregroundStyle(muted)
                    } else if option == .yearly, product == nil {
                        Text("$2.50/mo")
                            .font(.opaw(12, .semibold))
                            .foregroundStyle(muted)
                    }
                }
            }
            .foregroundStyle(ink)
            .padding(.horizontal, 18)
            .frame(height: 66)
            .background(surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(selected ? ink : (dark ? Color(hex: 0x2C2C30) : Color(hex: 0xEEEEEE)), lineWidth: selected ? 2.5 : 1.5)
            }
        }
        .buttonStyle(.pressable)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var timeline: some View {
        ZStack(alignment: .top) {
            Rectangle()
                .fill(ink.opacity(dark ? 0.25 : 0.18))
                .frame(height: 2.5)
                .padding(.horizontal, 6)
                .padding(.top, 5)
            HStack(alignment: .top) {
                step("Today", "Full access", filled: true, alignment: .leading)
                Spacer()
                step("Day 5", "Reminder", filled: false, alignment: .center)
                Spacer()
                step("Day 7", "Billing starts", filled: false, alignment: .trailing)
            }
        }
    }

    private func step(_ title: String, _ detail: String, filled: Bool, alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            Circle()
                .fill(filled ? ink : background)
                .overlay(Circle().strokeBorder(ink, lineWidth: 2.5))
                .frame(width: 12, height: 12)
                .padding(.bottom, 6)
            Text(title)
                .font(.opaw(13, .heavy))
                .foregroundStyle(ink)
            Text(detail)
                .font(.opaw(12, .medium))
                .foregroundStyle(muted)
        }
    }

    // MARK: Prices

    private func priceLabel(_ option: PremiumStore.Plan, product: Product?) -> String {
        let price = product?.displayPrice ?? (option == .yearly ? "$29.99" : "$4.99")
        return option == .yearly ? "\(price)/yr" : "\(price)/mo"
    }

    /// How much cheaper a year is than twelve months, as a whole percent.
    private var saving: Int? {
        guard let yearly = store.product(.yearly)?.price, let monthly = store.product(.monthly)?.price, monthly > 0 else {
            return store.products.isEmpty ? 50 : nil
        }
        let ratio = NSDecimalNumber(decimal: yearly / (monthly * 12)).doubleValue
        let percent = Int(((1 - ratio) * 100).rounded())
        return percent > 0 ? percent : nil
    }

    private var caption: String {
        let product = store.product(plan)
        let price = product?.displayPrice ?? (plan == .yearly ? "$29.99" : "$4.99")
        let period = plan == .yearly ? "year" : "month"
        return trialEligible ? "Then \(price)/\(period). Cancel anytime." : "\(price)/\(period). Cancel anytime."
    }

    private func buy() async {
        guard let product = store.product(plan) else { return }
        if await store.purchase(product) {
            Haptics.success()
            onClose(true)
        }
    }
}

/// One-time offer (5c): shown once after the paywall is closed, with a ten-minute timer.
struct OneTimeOfferView: View {
    let petImage: UIImage?
    /// Called with true when the offer was bought.
    let onDone: (Bool) -> Void

    @Environment(PremiumStore.self) private var store
    @State private var endsAt = Date().addingTimeInterval(10 * 60)
    @State private var appeared = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color(red: 20 / 255, green: 20 / 255, blue: 22 / 255).opacity(appeared ? 0.38 : 0)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                ZStack(alignment: .topLeading) {
                    PetSticker(image: petImage, edge: 5, motion: .bob, duration: 3.2)
                        .frame(width: 130, height: 148)
                        .padding(.leading, 34)
                        .padding(.top, 10)
                    SpeechBubble(text: "wait, one more thing")
                        .padding(.leading, 168)
                        .padding(.top, 18)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: 160, alignment: .top)
                .offset(y: 10)
                .zIndex(1)
                sheet
            }
            .offset(y: appeared ? 0 : 700)
        }
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.88)) { appeared = true }
        }
    }

    private var sheet: some View {
        VStack(spacing: 0) {
            SheetGrabber()
            HStack(spacing: 6) {
                Icon(.clock, size: 14)
                Text("One-time offer")
                    .font(.opaw(13, .heavy))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color.opawAlert, in: Capsule())
            .padding(.top, 20)

            TimelineView(.periodic(from: .now, by: 1)) { context in
                let left = max(0, Int(endsAt.timeIntervalSince(context.date)))
                HStack(spacing: 8) {
                    timeBox(String(format: "%02d", left / 60))
                    Text(":")
                        .font(.opaw(32, .black))
                        .foregroundStyle(Color(hex: 0xCCCCCC))
                    timeBox(String(format: "%02d", left % 60))
                }
                .onChange(of: left == 0) { _, ended in
                    if ended { onDone(false) }
                }
            }
            .padding(.top, 18)

            Text("Ends when the timer runs out")
                .font(.opaw(13, .semibold))
                .foregroundStyle(Color(hex: 0x999999))
                .padding(.top, 8)

            Text("50% off your first year")
                .font(.opaw(34, .black))
                .multilineTextAlignment(.center)
                .foregroundStyle(Color(hex: 0x111111))
                .padding(.top, 22)
                .padding(.horizontal, 40)

            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(regularPrice)
                    .font(.opaw(20, .bold))
                    .strikethrough()
                    .foregroundStyle(Color(hex: 0xAAAAAA))
                (Text(offerPrice).font(.opaw(30, .black)).foregroundColor(Color(hex: 0x111111))
                    + Text("/year").font(.opaw(16, .black)).foregroundColor(Color(hex: 0x999999)))
            }
            .padding(.top, 12)

            Text("\(offerMonthly)/month, renews at \(regularPrice)")
                .font(.opaw(14, .semibold))
                .foregroundStyle(Color(hex: 0x999999))

            Spacer(minLength: 20)

            Button {
                Task {
                    guard let product = store.offerProduct else { return }
                    if await store.purchase(product) {
                        Haptics.success()
                        onDone(true)
                    }
                }
            } label: {
                if store.isPurchasing { ProgressView().tint(.white) } else { Text("Claim offer") }
            }
            .buttonStyle(BlackPillButtonStyle(height: 62))
            .disabled(store.offerProduct == nil || store.isPurchasing)
            .padding(.horizontal, 20)

            Button("No thanks") { onDone(false) }
                .font(.opaw(15, .bold))
                .foregroundStyle(Color(hex: 0x999999))
                .frame(height: 44)
                .padding(.top, 2)
                .padding(.bottom, 4)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 560)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 40, topTrailingRadius: 40, style: .continuous)
                .fill(Color.white)
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private func timeBox(_ text: String) -> some View {
        Text(text)
            .font(.opaw(38, .black))
            .monospacedDigit()
            .foregroundStyle(Color(hex: 0x111111))
            .frame(width: 64, height: 72)
            .background(Color(hex: 0xF1F1F2), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var regularPrice: String { store.offerProduct?.displayPrice ?? store.product(.yearly)?.displayPrice ?? "$29.99" }

    private var offerPrice: String {
        store.offerProduct?.subscription?.introductoryOffer?.displayPrice ?? "$14.99"
    }

    private var offerMonthly: String {
        guard let offer = store.offerProduct?.subscription?.introductoryOffer, let product = store.offerProduct else { return "$1.25" }
        return (offer.price / 12).formatted(product.priceFormatStyle.precision(.fractionLength(2)))
    }
}

/// Presents the paywall from anywhere, with the active pet's name and sticker.
struct PaywallPresenter: ViewModifier {
    @Binding var isPresented: Bool
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]

    func body(content: Content) -> some View {
        content.fullScreenCover(isPresented: $isPresented) {
            let dog = dogs.first { $0.id == OpawStore.activeDogID } ?? dogs.first
            PaywallView(
                petName: dog?.name ?? "your pet",
                petImage: (dog?.cutoutData ?? dog?.photoData).flatMap(UIImage.init(data:))
            ) { _ in
                isPresented = false
            }
        }
    }
}

extension View {
    func premiumPaywall(isPresented: Binding<Bool>) -> some View {
        modifier(PaywallPresenter(isPresented: isPresented))
    }
}
