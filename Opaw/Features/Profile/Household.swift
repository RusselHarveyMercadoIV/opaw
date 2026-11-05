import SwiftUI

/// Someone else who looks after the pets, invited with a share link.
struct HouseholdMember: Codable, Identifiable, Hashable {
    enum Role: String, Codable, CaseIterable, Identifiable {
        case family, walker

        var id: String { rawValue }
        var title: String { self == .family ? "Family" : "Walker" }
        var detail: String { self == .family ? "can log everything" : "walks and poop only" }
    }

    var id = UUID()
    var name: String
    var role: Role
}

extension HouseholdMember {
    static let storageKey = "householdMembers"

    static func decode(_ data: Data) -> [HouseholdMember] {
        (try? JSONDecoder().decode([HouseholdMember].self, from: data)) ?? []
    }

    static func encode(_ members: [HouseholdMember]) -> Data {
        (try? JSONEncoder().encode(members)) ?? Data()
    }
}

/// The household list (4f): you as owner, then everyone invited, and the invite link card.
struct HouseholdCards: View {
    let petName: String
    @AppStorage(HouseholdMember.storageKey, store: OpawDefaults.shared) private var membersData = Data()
    @State private var showInvite = false

    private var members: [HouseholdMember] { HouseholdMember.decode(membersData) }

    var body: some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Household")
                    .font(.opaw(17, .black))
                    .foregroundStyle(Color.opawInk)
                    .padding(.top, 12)
                    .padding(.bottom, 8)
                row(name: "You", detail: "Owner · full access", chip: "Owner", owner: true)
                ForEach(members) { member in
                    row(name: member.name, detail: "\(member.role.title) · \(member.role.detail)", chip: "Invited", owner: false)
                        .contextMenu {
                            Button(role: .destructive) {
                                membersData = HouseholdMember.encode(members.filter { $0.id != member.id })
                            } label: {
                                Label("Remove \(member.name)", systemImage: "trash")
                            }
                        }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 4)
            .padding(.bottom, 6)
            .whiteCard()

            Button { showInvite = true } label: {
                HStack(spacing: 14) {
                    RowIcon(icon: .link, size: 44, iconSize: 20)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Share invite link")
                            .font(.opaw(16, .heavy))
                            .foregroundStyle(Color.opawInk)
                        Text("Pick a role before sending")
                            .font(.opaw(13, .medium))
                            .foregroundStyle(Color.opawMuted)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .whiteCard()
            }
            .buttonStyle(.pressable)
        }
        .sheet(isPresented: $showInvite) {
            InviteSheet(petName: petName) { member in
                membersData = HouseholdMember.encode(members + [member])
            }
        }
    }

    private func row(name: String, detail: String, chip: String, owner: Bool) -> some View {
        HStack(spacing: 12) {
            MiniFace(data: nil, size: 44, rim: 0, fill: Color.opawTrack, initial: owner ? nil : String(name.prefix(1)))
            VStack(alignment: .leading, spacing: 0) {
                Text(name)
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(Color.opawInk)
                Text(detail)
                    .font(.opaw(13, .medium))
                    .foregroundStyle(Color.opawMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Text(chip)
                .font(.opaw(13, .bold))
                .foregroundStyle(owner ? Color.white : Color(hex: 0x888888))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(owner ? Color(hex: 0x111111) : Color.opawChip, in: Capsule())
        }
        .padding(.vertical, 12)
        .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
    }
}

/// Pick who you're inviting and their role, then send the link.
private struct InviteSheet: View {
    let petName: String
    let onInvite: (HouseholdMember) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var role: HouseholdMember.Role = .family
    @State private var showShare = false

    private var trimmed: String { name.trimmingCharacters(in: .whitespaces) }

    private var message: String {
        "Help me look after \(petName) on Opaw! You're invited as \(role.title.lowercased()) (\(role.detail)). Everyone logs to the same pet, so meals never get doubled."
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Invite to the household")
                .font(.opaw(22, .black))
                .padding(.top, 28)
            TextField("Their name", text: $name)
                .font(.opaw(20, .heavy))
                .textInputAutocapitalization(.words)
                .padding(.horizontal, 18)
                .frame(height: 56)
                .background(Color.opawChip, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .padding(.top, 18)
            VStack(spacing: 8) {
                ForEach(HouseholdMember.Role.allCases) { option in
                    Button {
                        Haptics.select()
                        role = option
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(option.title).font(.opaw(16, .heavy))
                                Text(option.detail.prefix(1).uppercased() + option.detail.dropFirst())
                                    .font(.opaw(13, .medium))
                                    .foregroundStyle(role == option ? Color.white.opacity(0.7) : Color.opawMuted)
                            }
                            Spacer()
                        }
                        .foregroundStyle(role == option ? Color.white : Color.opawInk)
                        .padding(.horizontal, 18)
                        .frame(height: 60)
                        .background(role == option ? Color(hex: 0x111111) : Color.opawChip, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    .buttonStyle(.pressable)
                }
            }
            .padding(.top, 14)
            Spacer(minLength: 16)
            Button("Send invite") { showShare = true }
                .buttonStyle(BlackPillButtonStyle(height: 62))
                .disabled(trimmed.isEmpty)
        }
        .sheet(isPresented: $showShare) {
            ActivityShareSheet(items: [message]) { completed in
                showShare = false
                guard completed else { return }
                onInvite(HouseholdMember(name: trimmed, role: role))
                dismiss()
            }
            .presentationDetents([.medium, .large])
        }
        .foregroundStyle(Color.opawInk)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
        .presentationDetents([.height(440)])
        .presentationCornerRadius(40)
        .presentationBackground(Color.opawCream)
    }
}

/// Household, from Profile.
struct HouseholdView: View {
    let petName: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "Household") {
                Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Back")
            } trailing: {
                Color.clear.frame(width: 54, height: 54)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Everyone logs to the same pet, so meals never get doubled.")
                        .font(.opaw(15, .medium))
                        .foregroundStyle(Color.opawMuted)
                        .padding(.horizontal, 4)
                        .padding(.bottom, 14)
                    HouseholdCards(petName: petName)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.hidden)
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
        .hidesFloatingTabBar()
    }
}

/// The system share sheet, reporting whether something was actually sent.
struct ActivityShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    let onFinish: (Bool) -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.completionWithItemsHandler = { _, completed, _, _ in onFinish(completed) }
        return controller
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
