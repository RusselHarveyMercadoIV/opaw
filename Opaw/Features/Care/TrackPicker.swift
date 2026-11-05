import OpawKit
import SwiftData
import SwiftUI

// MARK: - Onboarding 4e pieces

/// The four-across grid of things to track. Tapping a circle turns it on or off.
struct TrackGrid: View {
    @Binding var trackedKinds: [String]
    /// When true, the last circle that's on can't be turned off.
    var keepsOne = false

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 4), spacing: 16) {
            ForEach(TrackOption.all) { option in
                optionButton(option)
            }
        }
    }

    private func isOn(_ option: TrackOption) -> Bool {
        option.keys.allSatisfy(trackedKinds.contains)
    }

    private func optionButton(_ option: TrackOption) -> some View {
        let on = isOn(option)
        return Button {
            if on {
                if keepsOne, TrackOption.all.filter { isOn($0) }.count <= 1 {
                    Haptics.soft()
                    return
                }
                trackedKinds.removeAll { option.keys.contains($0) }
            } else {
                trackedKinds.append(contentsOf: option.keys.filter { !trackedKinds.contains($0) })
            }
            Haptics.select()
        } label: {
            VStack(spacing: 6) {
                Group {
                    if option.icon == .activity {
                        Icon(.activity, size: 26)
                    } else {
                        Icon(option.icon, size: 26, weight: 2.3)
                    }
                }
                .foregroundStyle(on ? Color.white : Color(hex: 0x111111))
                .frame(width: 68, height: 68)
                .background(on ? Color(hex: 0x111111) : Color.white, in: Circle())
                .shadow(color: .black.opacity(0.06), radius: 12, y: 10)
                Text(option.title)
                    .font(.opaw(13, .bold))
                    .foregroundStyle(on ? Color.opawInk : Color.opawMuted)
                    .lineLimit(1)
            }
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(option.title)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// The "Meal times" card: a chip per meal, tap one to change or remove it, + to add one (up to five).
struct MealTimesCard: View {
    @Binding var mealTimes: [Int]
    var weightKg: Double?
    var species = "dog"

    @State private var editing: Int?
    @State private var newTime = Date()
    @State private var showTime = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Meal times")
                    .font(.opaw(17, .black))
                Spacer()
                if let grams = portionGrams {
                    Text("\(grams) g each")
                        .font(.opaw(13, .bold))
                        .foregroundStyle(Color.opawMuted)
                }
            }
            HStack(spacing: 8) {
                ForEach(Array(mealTimes.sorted().enumerated()), id: \.offset) { _, time in
                    Button {
                        editing = mealTimes.firstIndex(of: time)
                        newTime = Self.date(minutes: time)
                        showTime = true
                    } label: {
                        Text(String(format: "%02d:%02d", time / 60, time % 60))
                            .font(.opaw(15, .heavy))
                            .foregroundStyle(Color.opawInk)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.opawChip, in: Capsule())
                    }
                    .buttonStyle(.pressable)
                }
                if mealTimes.count < 5 {
                    Button {
                        editing = nil
                        newTime = Self.date(minutes: 12 * 60)
                        showTime = true
                    } label: {
                        Icon(.plus, size: 18)
                            .foregroundStyle(Color.opawInk)
                            .frame(width: 42, height: 42)
                            .background(Color.opawChip, in: Circle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Add meal time")
                }
            }
        }
        .foregroundStyle(Color.opawInk)
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .whiteCard()
        .sheet(isPresented: $showTime) {
            timeSheet
        }
    }

    private var timeSheet: some View {
        VStack(spacing: 0) {
            HStack {
                if let editing, mealTimes.count > 1 {
                    Button("Remove") {
                        mealTimes.remove(at: editing)
                        showTime = false
                    }
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(Color.opawAlert)
                }
                Spacer()
                Button("Done") {
                    let parts = Calendar.current.dateComponents([.hour, .minute], from: newTime)
                    let minutes = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
                    var times = mealTimes
                    if let editing {
                        times[editing] = minutes
                    } else if !times.contains(minutes) {
                        times.append(minutes)
                    }
                    mealTimes = Array(Set(times)).sorted()
                    showTime = false
                }
                .font(.opaw(16, .heavy))
                .foregroundStyle(Color.opawInk)
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)
            DatePicker("Meal time", selection: $newTime, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
            Spacer(minLength: 0)
        }
        .presentationDetents([.height(320)])
        .presentationCornerRadius(36)
        .presentationBackground(Color.opawCream)
    }

    /// Rough dry-food grams per meal: resting energy × 1.6, at about 3.6 kcal a gram.
    private var portionGrams: Int? {
        guard let kg = weightKg, kg > 0, !mealTimes.isEmpty, species == "dog" else { return nil }
        let kcal = 70 * pow(kg, 0.75) * 1.6
        let grams = kcal / 3.6 / Double(mealTimes.count)
        return Int((grams / 5).rounded() * 5)
    }

    private static func date(minutes: Int) -> Date {
        Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: Date()) ?? Date()
    }
}
