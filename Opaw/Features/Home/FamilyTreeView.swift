import PhotosUI
import SwiftData
import SwiftUI

/// Family tree (2i): parents above, siblings at the sides, the pet in the middle, and the pets at home.
struct FamilyTreeView: View {
    @Bindable var dog: Dog
    let onSelect: (Dog) -> Void

    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @Environment(\.dismiss) private var dismiss
    @State private var addRole: Relative.Role?
    @State private var editing: Relative?
    @State private var showProfile = false
    @State private var showAddDog = false

    private var mom: Relative? { dog.relatives.first { $0.role == .mom } }
    private var dad: Relative? { dog.relatives.first { $0.role == .dad } }
    private var siblings: [Relative] { dog.relatives.filter { $0.role == .brother || $0.role == .sister } }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "\(dog.name)'s family") {
                Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Back")
            } trailing: {
                Button { addRole = nextOpenRole } label: { RoundIcon(symbol: "plus") }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Add a relative")
            }
            ScrollView {
                VStack(spacing: 0) {
                    tree
                        .frame(height: 400)
                        .padding(.top, 18)
                    atHome
                        .padding(.horizontal, 20)
                }
                .padding(.bottom, 120)
            }
            .scrollIndicators(.hidden)
        }
        .overlay(alignment: .bottom) {
            ShareLink(item: inviteMessage) {
                Text(inviteTitle)
                    .font(.opaw(17, .bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 62)
                    .background(Color(hex: 0x111111), in: Capsule())
            }
            .buttonStyle(.pressable)
            .padding(.horizontal, 20)
            .padding(.bottom, 4)
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
        .hidesFloatingTabBar()
        .sheet(item: $addRole) { role in
            RelativeSheet(relative: Relative(name: "", role: role)) { save($0) }
        }
        .sheet(item: $editing) { relative in
            RelativeSheet(relative: relative, onDelete: { remove(relative) }) { save($0) }
        }
        .sheet(isPresented: $showProfile) { ProfileView() }
        .sheet(isPresented: $showAddDog) { AddDogSheet() }
    }

    // MARK: Tree

    private var tree: some View {
        GeometryReader { proxy in
            let sx = proxy.size.width / 390
            ZStack(alignment: .topLeading) {
                Group {
                    line(x: 95 * sx, y: 46, width: 200 * sx, height: 3)
                    line(x: 193 * sx, y: 46, width: 3, height: 118)
                    line(x: 65 * sx, y: 162, width: 260 * sx, height: 3)
                    line(x: 65 * sx, y: 162, width: 3, height: 40)
                    line(x: 322 * sx, y: 162, width: 3, height: 40)
                }
                .foregroundStyle(Color(hex: 0xD6D6D8))

                node(mom, role: .mom, size: 80)
                    .position(x: 95 * sx, y: 67)
                node(dad, role: .dad, size: 80)
                    .position(x: 295 * sx, y: 67)
                node(siblings.first, role: .brother, size: 76)
                    .position(x: 67 * sx, y: 259)
                node(siblings.dropFirst().first, role: .sister, size: 76)
                    .position(x: 323 * sx, y: 259)
                center
                    .position(x: 195 * sx, y: 249)
            }
        }
    }

    private func line(x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat) -> some View {
        Rectangle()
            .frame(width: width, height: height)
            .offset(x: x, y: y)
    }

    /// A relative's photo, name and role; an empty slot invites adding one.
    private func node(_ relative: Relative?, role: Relative.Role, size: CGFloat) -> some View {
        Button {
            if let relative { editing = relative } else { addRole = role }
        } label: {
            VStack(spacing: 6) {
                Group {
                    if let relative {
                        MiniFace(data: relative.photoData, size: size, rim: 4, fill: Color.opawChip, initial: String(relative.name.prefix(1)))
                            .shadow(color: .black.opacity(0.1), radius: 12, y: 10)
                    } else {
                        Icon(.plus, size: 22)
                            .foregroundStyle(Color.opawMuted)
                            .frame(width: size, height: size)
                            .overlay(Circle().strokeBorder(Color(hex: 0xC4C4C6), style: StrokeStyle(lineWidth: 2, dash: [5, 4])))
                    }
                }
                VStack(spacing: 2) {
                    Text(relative?.name ?? "Add \(role.title.lowercased())")
                        .font(.opaw(15, .black))
                        .foregroundStyle(relative == nil ? Color.opawMuted : Color.opawInk)
                    Text(subtitle(relative, role: role))
                        .font(.opaw(12, .semibold))
                        .foregroundStyle(Color.opawMuted)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            }
            .frame(width: 110)
        }
        .buttonStyle(.pressable)
    }

    private func subtitle(_ relative: Relative?, role: Relative.Role) -> String {
        guard let relative else { return role.title }
        return relative.place.isEmpty ? relative.role.title : "\(relative.role.title) · \(relative.place)"
    }

    private var center: some View {
        Button { showProfile = true } label: {
            VStack(spacing: 6) {
                MiniFace(data: dog.cutoutData ?? dog.photoData, size: 104, rim: 4, fill: Color.opawSurface)
                    .overlay(Circle().strokeBorder(Color(hex: 0x111111), lineWidth: 4))
                    .shadow(color: .black.opacity(0.1), radius: 12, y: 10)
                VStack(spacing: 2) {
                    Text(dog.name)
                        .font(.opaw(18, .black))
                        .foregroundStyle(Color.opawInk)
                    Text(ageLine)
                        .font(.opaw(12, .semibold))
                        .foregroundStyle(Color.opawMuted)
                }
                .lineLimit(1)
            }
            .frame(width: 110)
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("\(dog.name). Edit profile")
    }

    private var ageLine: String {
        let years = Int(dog.ageYears.rounded(.down))
        return dog.birthDate == nil ? "You" : "You · \(max(years, 0)) \(years == 1 ? "yr" : "yrs")"
    }

    // MARK: At home

    private var atHome: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text("At home")
                    .font(.opaw(18, .black))
                Spacer()
                Text("Swipe between them on Home")
                    .font(.opaw(13, .semibold))
                    .foregroundStyle(Color.opawMuted)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(dogs) { item in
                        Button {
                            onSelect(item)
                            Haptics.select()
                            dismiss()
                        } label: {
                            MiniFace(data: item.cutoutData ?? item.photoData, size: 58, rim: 0, fill: Color.opawChip)
                                .padding(3)
                                .overlay {
                                    if item.id == dog.id { Circle().strokeBorder(Color(hex: 0x111111), lineWidth: 3) }
                                }
                                .padding(-3)
                        }
                        .buttonStyle(.pressable)
                        .accessibilityLabel(item.name)
                    }
                    Button { showAddDog = true } label: {
                        Icon(.plus, size: 20)
                            .foregroundStyle(Color.opawMuted)
                            .frame(width: 58, height: 58)
                            .overlay(Circle().strokeBorder(Color(hex: 0xC4C4C6), style: StrokeStyle(lineWidth: 2, dash: [5, 4])))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Add a pet")
                }
                .padding(4)
            }
            .padding(-4)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .whiteCard(shadow: 0.06)
    }

    // MARK: Data

    private var nextOpenRole: Relative.Role {
        if mom == nil { return .mom }
        if dad == nil { return .dad }
        return siblings.count % 2 == 0 ? .brother : .sister
    }

    private var inviteTitle: String {
        if let sibling = siblings.first { return "Invite \(sibling.name)'s family" }
        return "Invite \(dog.name)'s family"
    }

    private var inviteMessage: String {
        "\(dog.name) is on Opaw, a little app where our pets come to life. Join \(dog.name)'s family tree!"
    }

    private func save(_ relative: Relative) {
        var all = dog.relatives
        if let index = all.firstIndex(where: { $0.id == relative.id }) {
            all[index] = relative
        } else {
            all.append(relative)
        }
        dog.relatives = all
        Haptics.success()
    }

    private func remove(_ relative: Relative) {
        dog.relatives = dog.relatives.filter { $0.id != relative.id }
    }
}

extension Relative.Role: Identifiable {
    var id: String { rawValue }
}

/// Adds or edits one relative: photo, name, role and where they live.
private struct RelativeSheet: View {
    @State var relative: Relative
    var onDelete: (() -> Void)?
    let onSave: (Relative) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var pick: PhotosPickerItem?

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Capsule()
                .fill(Color(hex: 0xDDDDDD))
                .frame(width: 40, height: 5)
                .frame(maxWidth: .infinity)
            HStack(spacing: 16) {
                PhotosPicker(selection: $pick, matching: .images) {
                    ZStack {
                        MiniFace(data: relative.photoData, size: 80, rim: 4, fill: Color.opawChip)
                        if relative.photoData == nil {
                            Icon(.camera, size: 24)
                                .foregroundStyle(Color.opawMuted)
                        }
                    }
                    .shadow(color: .black.opacity(0.1), radius: 12, y: 10)
                }
                Text(relative.name.isEmpty ? "New relative" : relative.name)
                    .font(.opaw(30, .black))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
            VStack(alignment: .leading, spacing: 10) {
                Text("Who")
                    .font(.opaw(14, .bold))
                    .foregroundStyle(Color.opawMuted)
                HStack(spacing: 6) {
                    ForEach(Relative.Role.allCases) { role in
                        ChoiceChip(title: role.title, isSelected: relative.role == role) { relative.role = role }
                    }
                }
            }
            field("Name", text: $relative.name)
            field("Where they live (optional)", text: $relative.place)
            Spacer(minLength: 0)
            Button("Save") {
                relative.name = relative.name.trimmingCharacters(in: .whitespacesAndNewlines)
                onSave(relative)
                dismiss()
            }
            .buttonStyle(.opawPrimary)
            .disabled(relative.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            if let onDelete {
                Button("Remove from tree", role: .destructive) {
                    onDelete()
                    dismiss()
                }
                .font(.opaw(15, .bold))
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 16)
        .presentationDetents([.large])
        .presentationCornerRadius(40)
        .onChange(of: pick) { _, item in
            Task {
                guard let data = try? await item?.loadTransferable(type: Data.self), let image = UIImage(data: data) else { return }
                relative.photoData = image.thumbnailJPEG(maxSide: 240)
            }
        }
    }

    private func field(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .font(.opaw(15, .medium))
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .background(Color(hex: 0xF6F6F7), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

extension UIImage {
    /// A small JPEG for avatars stored inline.
    func thumbnailJPEG(maxSide: CGFloat) -> Data? {
        let scale = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in draw(in: CGRect(origin: .zero, size: target)) }
        return resized.jpegData(compressionQuality: 0.8)
    }

    /// A small PNG that keeps transparency, for stickers.
    func thumbnailPNG(maxSide: CGFloat) -> Data? {
        let scale = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: target, format: format).pngData { _ in draw(in: CGRect(origin: .zero, size: target)) }
    }
}
