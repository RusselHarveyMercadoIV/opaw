import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Today's reminders as a timeline: done, due (in red, with a Log button) or later.
struct ScheduleView: View {
    let dog: Dog

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var showAdd = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            ScrollView {
                VStack(spacing: 14) {
                    ScreenHeader(title: "Schedule") {
                        Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                            .buttonStyle(.pressable)
                            .accessibilityLabel("Back")
                    } trailing: {
                        Button { showAdd = true } label: { RoundIcon(symbol: "plus") }
                            .buttonStyle(.pressable)
                            .accessibilityLabel("Add reminder")
                    }
                    todayCard(now: context.date)
                        .padding(.horizontal, Space.l)
                        .padding(.top, 6)
                    NavigationLink {
                        RemindersView(dog: dog)
                    } label: {
                        HStack {
                            Text("Manage reminders")
                                .font(.opaw(16, .heavy))
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(Color.opawMuted)
                        }
                        .foregroundStyle(Color.opawInk)
                        .card(padding: 20)
                    }
                    .buttonStyle(.pressable)
                    .padding(.horizontal, Space.l)
                }
                .padding(.bottom, 100)
            }
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showAdd) {
            AddReminderSheet(dog: dog) { reschedule() }
        }
    }

    private func todayCard(now: Date) -> some View {
        let reminders = dog.enabledReminders
        let done = reminders.filter { $0.isDone(on: now, for: dog) }.count
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Today")
                    .font(.opaw(18, .black))
                Spacer()
                Text("\(done) of \(reminders.count) done")
                    .font(.opaw(13, .bold))
                    .foregroundStyle(Color.opawMuted)
            }
            .padding(.vertical, 12)
            if reminders.isEmpty {
                Text("No reminders yet. Tap + to add meals, meds and more.")
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .padding(.vertical, 12)
            }
            ForEach(reminders) { reminder in
                row(reminder, now: now)
                    .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 4)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 15, y: 12)
    }

    private func row(_ reminder: Reminder, now: Date) -> some View {
        let due = reminder.dueDate(on: now)
        let isDone = reminder.isDone(on: now, for: dog)
        let minutesUntil = due.timeIntervalSince(now) / 60
        // Due soon (within 3 hours) or late counts as needing attention.
        let needsAttention = !isDone && minutesUntil < 180
        return HStack(spacing: 10) {
            Text(due.formatted(date: .omitted, time: .shortened))
                .font(.opaw(14, .bold))
                .foregroundStyle(needsAttention ? Color.opawAlert : Color.opawMuted)
                .frame(width: 58, alignment: .leading)
            ZStack {
                if isDone {
                    Circle().fill(Color.opawInk)
                    Image(systemName: "checkmark")
                        .font(.system(size: 9, weight: .black))
                        .foregroundStyle(Color.opawOnInk)
                } else {
                    Circle().strokeBorder(needsAttention ? Color.opawAlert : Color.opawInk.opacity(0.2), lineWidth: needsAttention ? 2.5 : 2)
                }
            }
            .frame(width: 18, height: 18)
            VStack(alignment: .leading, spacing: 1) {
                Text(reminder.title)
                    .font(.opaw(15, .heavy))
                    .foregroundStyle(needsAttention ? Color.opawAlert : Color.opawInk)
                Text(subtitle(reminder, due: due, isDone: isDone, minutesUntil: minutesUntil))
                    .font(.opaw(13, .medium))
                    .foregroundStyle(Color.opawMuted)
            }
            Spacer(minLength: 0)
            if needsAttention {
                Button("Log") { log(reminder) }
                    .font(.opaw(13, .heavy))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color.opawAlert, in: Capsule())
                    .buttonStyle(.pressable)
            }
        }
        .padding(.vertical, 12)
    }

    private func subtitle(_ reminder: Reminder, due: Date, isDone: Bool, minutesUntil: Double) -> String {
        if isDone { return reminder.kind.title }
        if minutesUntil < -30 { return "Late · \(reminder.kind.title.lowercased())" }
        if minutesUntil < 0 { return "Due now" }
        if minutesUntil < 60 { return "Due in \(Int(minutesUntil)) min" }
        if minutesUntil < 180 { return "Due in \(Int(minutesUntil / 60)) h" }
        return reminder.kind.title
    }

    private func log(_ reminder: Reminder) {
        modelContext.insert(CareEvent(kind: reminder.kind, note: reminder.title, dog: dog))
        try? modelContext.save()
        BadgeService.refresh(dog)
        try? modelContext.save()
        Haptics.success()
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
    }

    private func reschedule() {
        Task {
            await NotificationService.requestAuthorization()
            let all = (try? modelContext.fetch(FetchDescriptor<Reminder>())) ?? []
            await NotificationService.reschedule(all)
        }
    }
}
