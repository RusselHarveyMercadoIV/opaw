import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Schedule (3c): today's reminders as a timeline (done, due in red with a Log button, or later), then what's coming up.
struct ScheduleView: View {
    let dog: Dog

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var showAdd = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            ScrollView {
                VStack(spacing: 0) {
                    todayCard(now: context.date)
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                    let upcoming = dog.upcomingReminders(after: context.date)
                    if !upcoming.isEmpty {
                        comingUpCard(upcoming)
                            .padding(.horizontal, 20)
                            .padding(.top, 14)
                    }
                }
                .padding(.bottom, 60)
            }
            .scrollIndicators(.hidden)
            .safeAreaInset(edge: .top, spacing: 0) {
                ScreenHeader(title: "Schedule") {
                    Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Back")
                } trailing: {
                    Button { showAdd = true } label: { RoundIcon(symbol: "plus") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Add reminder")
                }
                .background(Color.opawCream)
            }
        }
        .overlay(alignment: .bottom) {
            LinearGradient(colors: [Color.opawCream.opacity(0), Color.opawCream], startPoint: .top, endPoint: .bottom)
                .frame(height: 40)
                .ignoresSafeArea(edges: .bottom)
                .allowsHitTesting(false)
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
        .hidesFloatingTabBar()
        .sheet(isPresented: $showAdd) {
            AddReminderSheet(dog: dog) { reschedule() }
        }
    }

    // MARK: Today

    private func todayCard(now: Date) -> some View {
        let reminders = dog.reminders(on: now)
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
            .padding(.top, 12)
            .padding(.bottom, 10)
            if reminders.isEmpty {
                Text("No reminders yet. Tap + to add meals, meds and more.")
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .padding(.vertical, 12)
                    .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
            ForEach(reminders) { reminder in
                row(reminder, now: now)
                    .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
                    .contextMenu { menu(reminder) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 6)
        .padding(.bottom, 4)
        .whiteCard()
    }

    private func row(_ reminder: Reminder, now: Date) -> some View {
        let due = reminder.dueDate(on: now)
        let isDone = reminder.isDone(on: now, for: dog)
        let minutesUntil = due.timeIntervalSince(now) / 60
        // Due soon (within 3 hours) or late counts as needing attention.
        let needsAttention = !isDone && minutesUntil < 180
        return HStack(spacing: 8) {
            Text(due.formatted(date: .omitted, time: .shortened))
                .font(.opaw(14, .bold))
                .foregroundStyle(needsAttention ? Color.opawAlert : Color.opawFaint)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(width: 54, alignment: .leading)
            ZStack {
                if isDone {
                    Circle().fill(Color(hex: 0x111111))
                    Icon(.check, size: 11)
                        .foregroundStyle(.white)
                } else if needsAttention {
                    Circle().strokeBorder(Color.opawAlert, lineWidth: 2.5)
                } else {
                    Circle().strokeBorder(Color(hex: 0xCCCCCC), lineWidth: 2)
                }
            }
            .frame(width: 18, height: 18)
            .frame(width: 22)
            VStack(alignment: .leading, spacing: 0) {
                Text(reminder.title)
                    .font(.opaw(15, .heavy))
                    .foregroundStyle(needsAttention ? Color.opawAlert : Color.opawInk)
                    .lineLimit(1)
                Text(subtitle(reminder, isDone: isDone, minutesUntil: minutesUntil, now: now))
                    .font(.opaw(13, .medium))
                    .foregroundStyle(Color.opawMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if needsAttention {
                Button { log(reminder) } label: {
                    Text("Log")
                        .font(.opaw(13, .heavy))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color.opawAlert, in: Capsule())
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("Log \(reminder.title)")
            }
        }
        .padding(.vertical, 12)
    }

    private func subtitle(_ reminder: Reminder, isDone: Bool, minutesUntil: Double, now: Date) -> String {
        if isDone {
            let due = reminder.dueDate(on: now)
            let event = (dog.careEvents ?? []).first {
                $0.kind == reminder.kind && $0.date >= due.addingTimeInterval(-90 * 60) && $0.date <= due.addingTimeInterval(6 * 3600)
            }
            return CareRecorder.name(event?.loggedBy ?? "")
        }
        let timing: String
        if minutesUntil < -30 {
            timing = "Late"
        } else if minutesUntil < 0 {
            timing = "Due now"
        } else if minutesUntil < 60 {
            timing = "Due in \(Int(minutesUntil)) min"
        } else if minutesUntil < 180 {
            timing = "Due in \(Int(minutesUntil / 60)) h"
        } else {
            return reminder.note.isEmpty ? reminder.kind.title : reminder.note.capitalizingFirst
        }
        return reminder.note.isEmpty ? timing : "\(timing) · \(reminder.note)"
    }

    // MARK: Coming up

    private func comingUpCard(_ items: [(date: Date, reminder: Reminder)]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Coming up")
                .font(.opaw(18, .black))
                .padding(.top, 12)
                .padding(.bottom, 8)
            ForEach(items.indices, id: \.self) { index in
                let item = items[index]
                HStack(spacing: 14) {
                    VStack(spacing: 0) {
                        Text(item.date.formatted(.dateTime.month(.abbreviated)).uppercased())
                            .font(.opaw(11, .bold))
                            .foregroundStyle(Color.opawMuted)
                        Text(item.date.formatted(.dateTime.day()))
                            .font(.opaw(19, .black))
                            .foregroundStyle(Color.opawInk)
                    }
                    .frame(width: 50, height: 54)
                    .background(Color.opawCream, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    VStack(alignment: .leading, spacing: 0) {
                        Text(item.reminder.title)
                            .font(.opaw(15, .heavy))
                            .foregroundStyle(Color.opawInk)
                        Text(item.reminder.repeats == .monthly ? "Monthly · reminds you the night before" : item.reminder.repeats.title)
                            .font(.opaw(13, .medium))
                            .foregroundStyle(Color.opawMuted)
                    }
                    .lineLimit(1)
                    Spacer(minLength: 0)
                }
                .padding(.vertical, 10)
                .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
                .contextMenu { menu(item.reminder) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 6)
        .whiteCard()
    }

    // MARK: Actions

    @ViewBuilder
    private func menu(_ reminder: Reminder) -> some View {
        Button(role: .destructive) {
            modelContext.delete(reminder)
            try? modelContext.save()
            reschedule()
        } label: {
            Label("Delete reminder", systemImage: "trash")
        }
    }

    private func log(_ reminder: Reminder) {
        _ = CareRecorder.log(CareEvent(kind: reminder.kind, note: reminder.title, dog: dog), dog: dog, in: modelContext)
    }

    private func reschedule() {
        Task {
            await NotificationService.requestAuthorization()
            let all = (try? modelContext.fetch(FetchDescriptor<Reminder>())) ?? []
            await NotificationService.reschedule(all)
        }
    }
}

private extension String {
    var capitalizingFirst: String { prefix(1).uppercased() + dropFirst() }
}
