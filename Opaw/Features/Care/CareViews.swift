import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Log any kind of care with optional details.
struct CareLogSheet: View {
    let dog: Dog
    var initialKind: CareKind = .meal
    var onSaved: () -> Void = {}

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var kind: CareKind = .meal
    @State private var didSetKind = false
    @State private var date = Date()
    @State private var note = ""
    @State private var consistency: PoopConsistency = .normal
    @State private var weight: Double?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 8)], spacing: 8) {
                        ForEach(CareKind.allCases) { option in
                            Button {
                                kind = option
                            } label: {
                                VStack(spacing: 4) {
                                    Image(systemName: option.symbol)
                                        .font(.title3)
                                        .foregroundStyle(option == kind ? Color.opawOnInk : Color.opawInk)
                                        .frame(width: 40, height: 40)
                                        .background(option == kind ? Color.opawInk : Color.opawChip, in: Circle())
                                    Text(option.title)
                                        .font(.caption)
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.8)
                                }
                                .frame(maxWidth: .infinity, minHeight: 60)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(option == kind ? Color.opawChip : Color.opawSurface)
                                )
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(option == kind ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 4)
                }

                Section("Details") {
                    DatePicker("When", selection: $date, in: ...Date())
                    if kind == .poop {
                        Picker("Consistency", selection: $consistency) {
                            ForEach(PoopConsistency.pickerCases) { Text($0.title).tag($0) }
                        }
                        if consistency.isConcerning {
                            Label("Keep an eye on this. If it continues for more than a day, call your vet.", systemImage: "exclamationmark.triangle")
                                .font(.footnote)
                                .foregroundStyle(Color.opawAlert)
                        }
                    }
                    if kind == .weight {
                        HStack {
                            Text("Weight (kg)")
                            Spacer()
                            TextField("kg", value: $weight, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                    TextField("Note (optional)", text: $note, axis: .vertical)
                }
            }
            .navigationTitle("Log for \(dog.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .disabled(kind == .weight && weight == nil)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear {
            guard !didSetKind else { return }
            didSetKind = true
            kind = initialKind
        }
    }

    private func save() {
        let event = CareEvent(kind: kind, date: date, note: note.trimmingCharacters(in: .whitespacesAndNewlines), dog: dog)
        if kind == .poop { event.poopConsistency = consistency }
        if kind == .weight, let weight {
            event.weightKg = weight
            dog.weightKg = weight
        }
        modelContext.insert(event)
        try? modelContext.save()
        Haptics.success()
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
        onSaved()
        dismiss()
    }
}

/// Every care event, grouped by day, with a filter.
struct CareHistoryView: View {
    let dog: Dog
    @Environment(\.modelContext) private var modelContext
    @State private var filter: CareKind?

    private var events: [CareEvent] {
        (dog.careEvents ?? [])
            .filter { filter == nil || $0.kind == filter }
            .sorted { $0.date > $1.date }
    }

    private var days: [(day: Date, events: [CareEvent])] {
        let grouped = Dictionary(grouping: events) { Calendar.current.startOfDay(for: $0.date) }
        return grouped.keys.sorted(by: >).map { ($0, grouped[$0] ?? []) }
    }

    var body: some View {
        List {
            if events.isEmpty {
                ContentUnavailableView("No logs yet", systemImage: "pawprint", description: Text("Logs you add will show up here."))
            }
            ForEach(days, id: \.day) { group in
                Section(group.day.formatted(.dateTime.weekday(.wide).month().day())) {
                    ForEach(group.events) { event in
                        CareEventRow(event: event)
                    }
                    .onDelete { offsets in
                        for index in offsets {
                            modelContext.delete(group.events[index])
                        }
                        try? modelContext.save()
                        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
                    }
                }
            }
        }
        .navigationTitle("History")
        .toolbar {
            Menu {
                Button("All") { filter = nil }
                ForEach(CareKind.allCases) { kind in
                    Button {
                        filter = kind
                    } label: {
                        Label(kind.title, systemImage: kind.symbol)
                    }
                }
            } label: {
                Label("Filter", systemImage: filter == nil ? "line.3.horizontal.decrease.circle" : "line.3.horizontal.decrease.circle.fill")
            }
        }
    }
}

/// Daily reminders for meals, meds and more.
struct RemindersView: View {
    @Bindable var dog: Dog
    @Environment(\.modelContext) private var modelContext
    @State private var showAdd = false

    private var reminders: [Reminder] {
        (dog.reminders ?? []).sorted { ($0.hour, $0.minute) < ($1.hour, $1.minute) }
    }

    var body: some View {
        List {
            if reminders.isEmpty {
                ContentUnavailableView("No reminders", systemImage: "bell", description: Text("Add reminders for meals, meds, flea treatment and more."))
            }
            ForEach(reminders) { reminder in
                ReminderRow(reminder: reminder, onChange: reschedule)
            }
            .onDelete { offsets in
                for index in offsets {
                    modelContext.delete(reminders[index])
                }
                try? modelContext.save()
                reschedule()
            }
        }
        .navigationTitle("Reminders")
        .toolbar {
            Button {
                showAdd = true
            } label: {
                Label("Add", systemImage: "plus")
            }
        }
        .sheet(isPresented: $showAdd) {
            AddReminderSheet(dog: dog) { reschedule() }
        }
    }

    private func reschedule() {
        Task {
            await NotificationService.requestAuthorization()
            let all = (try? modelContext.fetch(FetchDescriptor<Reminder>())) ?? []
            await NotificationService.reschedule(all)
        }
    }
}

private struct ReminderRow: View {
    @Bindable var reminder: Reminder
    let onChange: () -> Void

    var body: some View {
        Toggle(isOn: $reminder.isEnabled) {
            HStack(spacing: 12) {
                CareGlyph(symbol: reminder.kind.symbol)
                VStack(alignment: .leading) {
                    Text(reminder.title)
                    Text(String(format: "%02d:%02d", reminder.hour, reminder.minute))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
        }
        .onChange(of: reminder.isEnabled) { _, _ in onChange() }
    }
}

struct AddReminderSheet: View {
    let dog: Dog
    let onSaved: () -> Void

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var kind: CareKind = .meds
    @State private var title = ""
    @State private var note = ""
    @State private var repeats: ReminderRepeat = .daily
    @State private var startDate = Date()
    @State private var time = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: Date()) ?? Date()

    var body: some View {
        NavigationStack {
            Form {
                Picker("Type", selection: $kind) {
                    ForEach(CareKind.allCases) { Label($0.title, systemImage: $0.symbol).tag($0) }
                }
                TextField("Title", text: $title, prompt: Text("\(dog.name)'s \(kind.title.lowercased())"))
                TextField("Detail", text: $note, prompt: Text("e.g. with food"))
                Picker("Repeats", selection: $repeats) {
                    ForEach(ReminderRepeat.allCases) { Text($0.title).tag($0) }
                }
                if repeats != .daily {
                    DatePicker("Next on", selection: $startDate, in: Date()..., displayedComponents: .date)
                }
                DatePicker("Time", selection: $time, displayedComponents: .hourAndMinute)
            }
            .navigationTitle("New reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        let parts = Calendar.current.dateComponents([.hour, .minute], from: time)
                        let finalTitle = title.isEmpty ? "\(dog.name)'s \(kind.title.lowercased())" : title
                        modelContext.insert(Reminder(
                            title: finalTitle,
                            kind: kind,
                            hour: parts.hour ?? 9,
                            minute: parts.minute ?? 0,
                            repeats: repeats,
                            startDate: startDate,
                            note: note.trimmingCharacters(in: .whitespacesAndNewlines),
                            dog: dog
                        ))
                        try? modelContext.save()
                        onSaved()
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.large])
    }
}
