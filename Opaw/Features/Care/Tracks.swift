import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

// MARK: - Tracks

/// One circle on the Care grid: a built-in kind or a custom track.
/// A value copy, so a view never reads a track that was just deleted.
struct CareTrack: Identifiable, Equatable {
    let key: String
    let kind: CareKind
    let trackID: UUID?
    let title: String
    /// An SF Symbol name or a `DesignIcon` raw value, for `SymbolIcon`.
    let icon: String
    let logStyle: TrackLogStyle

    var id: String { key }
    var isCustom: Bool { trackID != nil }
}

extension Dog {
    /// The built-in tracks in the design's order.
    static let builtInTracks: [CareKind] = [.meal, .water, .pee, .poop, .meds, .bath, .brush, .nails, .weight, .vet]

    /// Every track in the owner's order (3h), shown or not. Tracks missing from the saved order go last.
    var allTracks: [CareTrack] {
        let builtIns = Dog.builtInTracks.map {
            CareTrack(key: $0.rawValue, kind: $0, trackID: nil, title: $0.shortTitle, icon: $0.symbol, logStyle: .count)
        }
        let customs = (customTracks ?? []).sorted { $0.createdAt < $1.createdAt }.map {
            CareTrack(key: $0.key, kind: .custom, trackID: $0.id, title: $0.name, icon: $0.icon, logStyle: $0.logStyle)
        }
        let all = builtIns + customs
        guard !trackOrderRaw.isEmpty else { return all }
        let position = Dictionary(trackOrderRaw.enumerated().map { ($1, $0) }, uniquingKeysWith: { first, _ in first })
        return all.enumerated()
            .sorted { (position[$0.element.key] ?? trackOrderRaw.count + $0.offset) < (position[$1.element.key] ?? trackOrderRaw.count + $1.offset) }
            .map(\.element)
    }

    /// The tracks shown on the Care grid, in order.
    var shownTracks: [CareTrack] {
        allTracks.filter { tracks($0.key) }
    }
}

extension CareEvent {
    /// The custom track's name for a custom log, otherwise the kind's title.
    var displayTitle: String {
        kind == .custom && !trackName.isEmpty ? trackName : kind.title
    }
}

/// Re-plans every reminder notification after a change.
@MainActor
func rescheduleAllReminders(in context: ModelContext) {
    let all = (try? context.fetch(FetchDescriptor<Reminder>())) ?? []
    Task { await NotificationService.reschedule(all) }
}

// MARK: - 3h Edit tracks

/// Show or hide tracks, drag them into order, and add custom ones (3h). Changes save as you make them.
struct EditTracksSheet: View {
    let dog: Dog

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @State private var order: [CareTrack] = []
    @State private var shown: Set<String> = []
    @State private var dragKey: String?
    @State private var dragStart = 0
    @State private var dragOffset: CGFloat = 0
    @State private var editor: TrackEditorTarget?

    private let rowHeight: CGFloat = 57

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Edit tracks")
                    .font(.opaw(30, .black))
                    .foregroundStyle(Color.opawInk)
                Spacer()
                Button("Done") { dismiss() }
                    .font(.opaw(15, .heavy))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color(hex: 0x111111), in: Capsule())
                    .buttonStyle(.pressable)
            }
            .padding(.top, 34)
            Text("Choose what shows on Care. Drag to reorder.")
                .font(.opaw(15, .medium))
                .foregroundStyle(Color.opawMuted)
                .padding(.top, 4)
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Array(order.enumerated()), id: \.element.key) { index, track in
                        row(track, index: index)
                    }
                    Button { editor = .new } label: {
                        HStack(spacing: 8) {
                            Icon(.plus, size: 18)
                            Text("Add custom track")
                        }
                        .font(.opaw(16, .heavy))
                        .foregroundStyle(Color.opawInk)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .overlay(Capsule().strokeBorder(Color(hex: 0xD4D4D8), style: StrokeStyle(lineWidth: 2, dash: [6, 5])))
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.pressable)
                    .padding(.top, 12)
                }
                .padding(.top, 10)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .scrollDisabled(dragKey != nil)
        }
        .padding(.horizontal, 24)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(40)
        .presentationBackground(Color.opawSurface)
        .onAppear(perform: load)
        .sheet(item: $editor, onDismiss: load) { target in
            TrackEditorSheet(dog: dog, trackID: target.trackID)
        }
    }

    private func row(_ track: CareTrack, index: Int) -> some View {
        let isOn = shown.contains(track.key)
        let isDragging = dragKey == track.key
        return HStack(spacing: 12) {
            GripDots()
                .foregroundStyle(Color(hex: 0xCCCCCC))
                .frame(width: 24, height: rowHeight)
                .contentShape(Rectangle())
                .gesture(drag(track))
                .accessibilityHidden(true)
            Button {
                if let id = track.trackID { editor = .edit(id) }
            } label: {
                HStack(spacing: 12) {
                    SymbolIcon(symbol: track.icon, size: 18)
                        .foregroundStyle(isOn ? Color.opawInk : Color.opawFaint)
                        .frame(width: 38, height: 38)
                        .background(Color.opawChip, in: Circle())
                    Text(track.title)
                        .font(.opaw(16, .bold))
                        .foregroundStyle(isOn ? Color.opawInk : Color.opawMuted)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    if track.isCustom {
                        Text("Custom")
                            .font(.opaw(12, .bold))
                            .foregroundStyle(Color.opawMuted)
                            .padding(.trailing, 4)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!track.isCustom)
            .accessibilityHint(track.isCustom ? "Edit this track" : "")
            DesignToggle(isOn: Binding(get: { isOn }, set: { setShown(track, $0) }))
                .accessibilityLabel("Show \(track.title)")
        }
        .padding(.leading, -3)
        .frame(height: rowHeight)
        .background {
            if isDragging {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.opawSurface)
                    .shadow(color: .black.opacity(0.12), radius: 14, y: 6)
                    .padding(.horizontal, -10)
            }
        }
        .overlay(alignment: .top) {
            if index > 0 && !isDragging {
                Rectangle().fill(Color.opawHairline).frame(height: 1)
            }
        }
        .offset(y: isDragging ? dragOffset : 0)
        .zIndex(isDragging ? 1 : 0)
    }

    private func drag(_ track: CareTrack) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .onChanged { value in
                guard let current = order.firstIndex(where: { $0.key == track.key }) else { return }
                if dragKey == nil {
                    dragKey = track.key
                    dragStart = current
                    Haptics.select()
                }
                let target = min(max(dragStart + Int((value.translation.height / rowHeight).rounded()), 0), order.count - 1)
                if target != current {
                    withAnimation(.snappy(duration: 0.2)) {
                        order.move(fromOffsets: IndexSet(integer: current), toOffset: target > current ? target + 1 : target)
                    }
                    Haptics.select()
                }
                let now = order.firstIndex(where: { $0.key == track.key }) ?? current
                dragOffset = value.translation.height - CGFloat(now - dragStart) * rowHeight
            }
            .onEnded { _ in
                withAnimation(.snappy(duration: 0.2)) {
                    dragKey = nil
                    dragOffset = 0
                }
                save()
            }
    }

    private func setShown(_ track: CareTrack, _ on: Bool) {
        if on {
            shown.insert(track.key)
        } else {
            // Care always keeps at least one circle besides Edit.
            guard shown.count > 1 else { return }
            shown.remove(track.key)
        }
        save()
    }

    private func load() {
        order = dog.allTracks
        shown = Set(order.filter { dog.tracks($0.key) }.map(\.key))
    }

    private func save() {
        let keys = order.map(\.key)
        // Keep keys that aren't circles on Care, such as "walks" from setup.
        let extras = dog.trackedKindsRaw.isEmpty
            ? [TrackOption.walkKey]
            : dog.trackedKindsRaw.filter { !keys.contains($0) && !$0.hasPrefix("custom:") }
        dog.trackOrderRaw = keys
        dog.trackedKindsRaw = extras + keys.filter { shown.contains($0) }
        try? modelContext.save()
        WidgetCenter.shared.reloadAllTimelines()
    }
}

/// Six dots, the drag handle in 3h.
private struct GripDots: View {
    var body: some View {
        Canvas { context, size in
            let origin = CGPoint(x: (size.width - 18) / 2, y: (size.height - 18) / 2)
            for x in [9.0, 15.0] {
                for y in [6.0, 12.0, 18.0] {
                    let center = CGPoint(x: origin.x + x * 0.75, y: origin.y + y * 0.75)
                    context.fill(Path(ellipseIn: CGRect(x: center.x - 1.6, y: center.y - 1.6, width: 3.2, height: 3.2)), with: .foreground)
                }
            }
        }
    }
}

enum TrackEditorTarget: Identifiable {
    case new
    case edit(UUID)

    var id: String {
        switch self {
        case .new: return "new"
        case .edit(let id): return id.uuidString
        }
    }

    var trackID: UUID? {
        if case .edit(let id) = self { return id }
        return nil
    }
}

// MARK: - 3i New track

/// Make or change a custom track: its name, icon, how it's logged, and a daily reminder (3i).
struct TrackEditorSheet: View {
    let dog: Dog
    /// Nil for a new track.
    let trackID: UUID?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @State private var name = ""
    @State private var icon: DesignIcon = .sparkle
    @State private var style: TrackLogStyle = .done
    @State private var remind = true
    @State private var remindMinutes = 21 * 60
    @State private var showMoreIcons = false
    @State private var showTime = false
    @State private var confirmDelete = false
    @State private var loaded = false
    @FocusState private var nameFocused: Bool

    static let icons: [DesignIcon] = [.sparkle, .bone, .nails, .heartLine, .star, .moon, .sun, .book, .ball]
    static let moreIcons: [DesignIcon] = [.paw, .water, .meal, .meds, .bath, .brush, .weight, .vet, .camera, .bell]

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var existing: CustomTrack? {
        guard let trackID else { return nil }
        return dog.customTracks?.first { $0.id == trackID }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(trackID == nil ? "New track" : "Edit track")
                    .font(.opaw(30, .black))
                    .foregroundStyle(Color.opawInk)
                Spacer()
                Button("Cancel") { dismiss() }
                    .font(.opaw(15, .bold))
                    .foregroundStyle(Color.opawMuted)
            }
            .padding(.top, 34)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    nameCard
                        .padding(.top, 16)
                    sectionLabel("Icon")
                        .padding(.top, 20)
                    iconGrid
                        .padding(.top, 10)
                    sectionLabel("Log it as")
                        .padding(.top, 20)
                    HStack(spacing: 6) {
                        ForEach(TrackLogStyle.allCases) { option in
                            let selected = option == style
                            Button {
                                Haptics.select()
                                style = option
                            } label: {
                                Text(option.title)
                                    .font(.opaw(14, .bold))
                                    .foregroundStyle(selected ? Color.white : Color(hex: 0x555555))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 11)
                                    .background(selected ? Color(hex: 0x111111) : Color.opawChip, in: Capsule())
                            }
                            .buttonStyle(.pressable)
                            .accessibilityAddTraits(selected ? .isSelected : [])
                        }
                    }
                    .padding(.top, 10)
                    remindRow
                        .padding(.top, 20)
                    if trackID != nil {
                        Button("Delete track") { confirmDelete = true }
                            .font(.opaw(15, .heavy))
                            .foregroundStyle(Color.opawAlert)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 28)
                    }
                }
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            Button(trackID == nil ? "Add track" : "Save", action: save)
                .buttonStyle(BlackPillButtonStyle(height: 62))
                .disabled(trimmedName.isEmpty)
                .padding(.horizontal, -4)
                .padding(.bottom, 8)
        }
        .padding(.horizontal, 24)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(40)
        .presentationBackground(Color.opawSurface)
        .onAppear(perform: load)
        .sheet(isPresented: $showTime) { timeSheet }
        .confirmationDialog("Delete \(trimmedName.isEmpty ? "this track" : trimmedName)?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete track", role: .destructive, action: delete)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("It comes off Care and its reminder stops. Past logs stay in your history.")
        }
    }

    private var nameCard: some View {
        HStack(spacing: 14) {
            Icon(icon, size: 22)
                .foregroundStyle(.white)
                .frame(width: 52, height: 52)
                .background(Color(hex: 0x111111), in: Circle())
            VStack(alignment: .leading, spacing: 0) {
                Text("Name")
                    .font(.opaw(12, .semibold))
                    .foregroundStyle(Color.opawMuted)
                TextField("Brushing teeth", text: $name)
                    .font(.opaw(22, .black))
                    .foregroundStyle(Color.opawInk)
                    .tint(Color.opawInk)
                    .focused($nameFocused)
                    .submitLabel(.done)
                    .textInputAutocapitalization(.sentences)
            }
        }
        .padding(.vertical, 12)
        .padding(.leading, 12)
        .padding(.trailing, 16)
        .background(Color.opawChip.opacity(0.6), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .onTapGesture { nameFocused = true }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.opaw(14, .bold))
            .foregroundStyle(Color.opawMuted)
    }

    /// Five across, spread edge to edge. The dashed + shows more icons.
    private var iconGrid: some View {
        let options = showMoreIcons ? Self.icons + Self.moreIcons : Self.icons
        var cells: [IconCell] = options.map { .icon($0) }
        if !showMoreIcons { cells.append(.more) }
        while cells.count % 5 != 0 { cells.append(.blank(cells.count)) }
        let rows = stride(from: 0, to: cells.count, by: 5).map { Array(cells[$0..<($0 + 5)]) }
        return VStack(spacing: 12) {
            ForEach(rows.indices, id: \.self) { rowIndex in
                HStack(spacing: 0) {
                    ForEach(Array(rows[rowIndex].enumerated()), id: \.element.id) { column, cell in
                        if column > 0 { Spacer(minLength: 4) }
                        iconCell(cell)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func iconCell(_ cell: IconCell) -> some View {
        switch cell {
        case .icon(let option):
            let selected = option == icon
            Button {
                Haptics.select()
                icon = option
            } label: {
                Icon(option, size: 22, weight: option.style == .stroke ? 2.3 : nil)
                    .foregroundStyle(selected ? Color.white : Color.opawInk)
                    .frame(width: 52, height: 52)
                    .background(selected ? Color(hex: 0x111111) : Color.opawChip, in: Circle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(option.rawValue)
            .accessibilityAddTraits(selected ? .isSelected : [])
        case .more:
            Button {
                Haptics.select()
                withAnimation(.snappy) { showMoreIcons = true }
            } label: {
                Icon(.plus, size: 18)
                    .foregroundStyle(Color.opawMuted)
                    .frame(width: 52, height: 52)
                    .overlay(Circle().strokeBorder(Color(hex: 0xD4D4D8), style: StrokeStyle(lineWidth: 2, dash: [4, 4])))
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("More icons")
        case .blank:
            Color.clear.frame(width: 52, height: 52)
        }
    }

    private var remindRow: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Remind me")
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(Color.opawInk)
                Button { showTime = true } label: {
                    Text("Daily · \(String(format: "%02d:%02d", remindMinutes / 60, remindMinutes % 60))")
                        .font(.opaw(13, .medium))
                        .foregroundStyle(Color.opawMuted)
                }
                .buttonStyle(.plain)
                .disabled(!remind)
                .accessibilityHint("Change the reminder time")
            }
            Spacer()
            DesignToggle(isOn: $remind)
                .accessibilityLabel("Remind me")
        }
        .padding(.top, 14)
        .overlay(alignment: .top) {
            Rectangle().fill(Color.opawHairline).frame(height: 1)
        }
    }

    private var timeSheet: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button("Done") { showTime = false }
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(Color.opawInk)
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)
            DatePicker("Reminder time", selection: Binding(
                get: { Calendar.current.date(bySettingHour: remindMinutes / 60, minute: remindMinutes % 60, second: 0, of: Date()) ?? Date() },
                set: {
                    let parts = Calendar.current.dateComponents([.hour, .minute], from: $0)
                    remindMinutes = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
                }
            ), displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
            Spacer(minLength: 0)
        }
        .presentationDetents([.height(320)])
        .presentationCornerRadius(36)
        .presentationBackground(Color.opawCream)
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        guard let existing else {
            nameFocused = true
            return
        }
        name = existing.name
        icon = DesignIcon(rawValue: existing.icon) ?? .sparkle
        style = existing.logStyle
        showMoreIcons = !Self.icons.contains(icon)
        if let reminder = (dog.reminders ?? []).first(where: { $0.trackID == existing.id }) {
            remind = reminder.isEnabled
            remindMinutes = reminder.hour * 60 + reminder.minute
        } else {
            remind = false
        }
    }

    private func save() {
        let title = trimmedName
        guard !title.isEmpty else { return }
        let track: CustomTrack
        if let existing {
            existing.name = title
            existing.icon = icon.rawValue
            existing.logStyle = style
            track = existing
        } else {
            track = CustomTrack(name: title, icon: icon.rawValue, logStyle: style, dog: dog)
            modelContext.insert(track)
            if !dog.trackOrderRaw.isEmpty { dog.trackOrderRaw.append(track.key) }
            if !dog.trackedKindsRaw.isEmpty { dog.trackedKindsRaw.append(track.key) }
        }

        let reminders = (dog.reminders ?? []).filter { $0.trackID == track.id }
        if remind {
            if let reminder = reminders.first {
                reminder.title = title
                reminder.hour = remindMinutes / 60
                reminder.minute = remindMinutes % 60
                reminder.isEnabled = true
                reminders.dropFirst().forEach { modelContext.delete($0) }
            } else {
                let reminder = Reminder(title: title, kind: .custom, hour: remindMinutes / 60, minute: remindMinutes % 60, dog: dog)
                reminder.trackID = track.id
                modelContext.insert(reminder)
            }
        } else {
            reminders.forEach { modelContext.delete($0) }
        }
        try? modelContext.save()
        WidgetCenter.shared.reloadAllTimelines()
        if remind {
            Task {
                if await NotificationService.requestAuthorization() {
                    rescheduleAllReminders(in: modelContext)
                }
            }
        } else {
            rescheduleAllReminders(in: modelContext)
        }
        Haptics.success()
        dismiss()
    }

    private func delete() {
        guard let existing else { return }
        let key = existing.key
        let id = existing.id
        dog.trackOrderRaw.removeAll { $0 == key }
        dog.trackedKindsRaw.removeAll { $0 == key }
        for reminder in (dog.reminders ?? []) where reminder.trackID == id {
            modelContext.delete(reminder)
        }
        modelContext.delete(existing)
        try? modelContext.save()
        WidgetCenter.shared.reloadAllTimelines()
        rescheduleAllReminders(in: modelContext)
        dismiss()
    }
}

private enum IconCell: Identifiable {
    case icon(DesignIcon)
    case more
    case blank(Int)

    var id: String {
        switch self {
        case .icon(let icon): return icon.rawValue
        case .more: return "more"
        case .blank(let index): return "blank-\(index)"
        }
    }
}

// MARK: - Amount log

/// Asks for a number when a custom track is logged as an amount.
struct AmountLogSheet: View {
    let track: CareTrack
    let onLog: (Double) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @FocusState private var focused: Bool

    private var value: Double? {
        guard let number = Double(text.replacingOccurrences(of: ",", with: ".")), number > 0 else { return nil }
        return number
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(track.title)
                    .font(.opaw(26, .black))
                    .foregroundStyle(Color.opawInk)
                    .lineLimit(1)
                Spacer()
                Button("Cancel") { dismiss() }
                    .font(.opaw(15, .bold))
                    .foregroundStyle(Color.opawMuted)
            }
            .padding(.top, 30)
            TextField("Amount", text: $text)
                .keyboardType(.decimalPad)
                .font(.opaw(32, .black))
                .foregroundStyle(Color.opawInk)
                .tint(Color.opawInk)
                .focused($focused)
                .padding(.horizontal, 20)
                .frame(height: 72)
                .background(Color.opawChip.opacity(0.6), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .padding(.top, 16)
            Spacer(minLength: 16)
            Button("Log") {
                guard let value else { return }
                onLog(value)
                dismiss()
            }
            .buttonStyle(BlackPillButtonStyle(height: 62))
            .disabled(value == nil)
            .padding(.bottom, 8)
        }
        .padding(.horizontal, 24)
        .presentationDetents([.height(300)])
        .presentationCornerRadius(40)
        .presentationBackground(Color.opawSurface)
        .onAppear { focused = true }
    }
}
