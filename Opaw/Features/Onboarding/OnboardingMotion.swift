import SwiftUI

/// Progress as a trail of paw prints walking left to right.
struct PawTrail: View {
    let current: Int
    let total: Int

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<total, id: \.self) { index in
                Image(systemName: "pawprint.fill")
                    .font(.system(size: 13))
                    .rotationEffect(.degrees(90))
                    .offset(y: index.isMultiple(of: 2) ? -4 : 4)
                    .foregroundStyle(index <= current ? Color.opawOrange : Color.opawInk.opacity(0.12))
                    .scaleEffect(index == current ? 1.25 : 1)
            }
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.6), value: current)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Step \(current + 1) of \(total)")
    }
}

/// Paw prints that walk across the screen along a gentle curve, then fade, on a loop.
struct WalkingPaws: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let count = 11
    private let stepInterval = 0.34
    private var cycle: Double { Double(count) * stepInterval + 1.8 }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { timeline in
            Canvas { context, size in
                let time = timeline.date.timeIntervalSinceReferenceDate
                let phase = time.truncatingRemainder(dividingBy: cycle)
                var paw = context.resolve(Image(systemName: "pawprint.fill"))
                paw.shading = .color(.opawOrange)

                for index in 0..<count {
                    let u = Double(index) / Double(count - 1)
                    let point = position(u, in: size)
                    let ahead = position(min(1, u + 0.02), in: size)
                    let behind = position(max(0, u - 0.02), in: size)
                    let angle = Double(atan2(ahead.y - behind.y, ahead.x - behind.x))
                    let side: Double = index.isMultiple(of: 2) ? 1 : -1
                    let normal = angle + Double.pi / 2
                    let x = Double(point.x) + cos(normal) * 11 * side
                    let y = Double(point.y) + sin(normal) * 11 * side

                    let opacity: Double
                    if reduceMotion {
                        opacity = 0.25 + 0.6 * u
                    } else {
                        let age = phase - Double(index) * stepInterval
                        if age < 0 {
                            opacity = 0
                        } else {
                            let fadeIn = min(1, age / 0.2)
                            let life = cycle - Double(index) * stepInterval
                            let fadeOut = max(0, 1 - pow(age / life, 3))
                            opacity = fadeIn * fadeOut
                        }
                    }

                    var layer = context
                    layer.opacity = opacity
                    layer.translateBy(x: x, y: y)
                    layer.rotate(by: .radians(angle + .pi / 2))
                    layer.scaleBy(x: 1.35, y: 1.35)
                    layer.draw(paw, at: .zero)
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// A soft S-curve from bottom left to top right.
    private func position(_ u: Double, in size: CGSize) -> CGPoint {
        let width = Double(size.width)
        let height = Double(size.height)
        let x: Double = width * (0.06 + 0.88 * u)
        let y: Double = height * (0.88 - 0.72 * u) + sin(u * Double.pi * 2) * height * 0.1
        return CGPoint(x: x, y: y)
    }
}

/// Paw prints that drift upward and fade, for the reveal.
struct PawConfetti: View {
    let isActive: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start = Date()

    private struct Particle {
        let x: Double
        let speed: Double
        let delay: Double
        let size: Double
        let spin: Double
    }

    private let particles: [Particle] = (0..<16).map { index in
        // Deterministic spread so every reveal looks composed, not noisy.
        let golden = Double(index) * 0.618_033_988_75
        return Particle(
            x: golden.truncatingRemainder(dividingBy: 1),
            speed: 0.10 + 0.06 * Double(index % 4),
            delay: Double(index) * 0.11,
            size: 0.8 + 0.5 * Double(index % 3),
            spin: Double(index % 5) - 2
        )
    }

    var body: some View {
        TimelineView(.animation(paused: !isActive || reduceMotion)) { timeline in
            Canvas { context, size in
                guard isActive, !reduceMotion else { return }
                let elapsed = timeline.date.timeIntervalSince(start)
                var paw = context.resolve(Image(systemName: "pawprint.fill"))
                paw.shading = .color(.opawOrange)
                for particle in particles {
                    let t = elapsed - particle.delay
                    guard t > 0 else { continue }
                    let progress = t * particle.speed
                    guard progress < 1 else { continue }
                    let x: Double = Double(size.width) * (0.08 + 0.84 * particle.x) + sin(t * 1.6 + particle.spin) * 10
                    let y: Double = Double(size.height) * (0.78 - 0.7 * progress)
                    var layer = context
                    layer.opacity = sin(progress * Double.pi) * 0.55
                    layer.translateBy(x: x, y: y)
                    layer.rotate(by: .degrees(particle.spin * 12 + t * particle.spin * 8))
                    layer.scaleBy(x: particle.size, y: particle.size)
                    layer.draw(paw, at: .zero)
                }
            }
        }
        .onChange(of: isActive) { _, active in
            if active { start = Date() }
        }
        .accessibilityHidden(true)
    }
}

/// A soft accent band that sweeps over the photo while it is analyzed.
struct ScanSweep: View {
    @State private var atBottom = false

    var body: some View {
        ZStack {
            Color.white.opacity(0.18)
            LinearGradient(
                colors: [Color.opawOrange.opacity(0), Color.opawOrange.opacity(0.35)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 110)
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(Color.opawOrange)
                    .frame(height: 2)
                    .shadow(color: Color.opawOrange.opacity(0.8), radius: 6)
            }
            .frame(maxHeight: .infinity, alignment: atBottom ? .bottom : .top)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.3).repeatForever(autoreverses: true)) {
                atBottom = true
            }
        }
        .accessibilityHidden(true)
    }
}

/// Four viewfinder corners.
struct CornerBrackets: Shape {
    var length: CGFloat = 30
    var radius: CGFloat = 18

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let r = min(radius, length)
        // Top left
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + length))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + r))
        path.addQuadCurve(to: CGPoint(x: rect.minX + r, y: rect.minY), control: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX + length, y: rect.minY))
        // Top right
        path.move(to: CGPoint(x: rect.maxX - length, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: rect.minY + r), control: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + length))
        // Bottom right
        path.move(to: CGPoint(x: rect.maxX, y: rect.maxY - length))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r))
        path.addQuadCurve(to: CGPoint(x: rect.maxX - r, y: rect.maxY), control: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX - length, y: rect.maxY))
        // Bottom left
        path.move(to: CGPoint(x: rect.minX + length, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX + r, y: rect.maxY))
        path.addQuadCurve(to: CGPoint(x: rect.minX, y: rect.maxY - r), control: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - length))
        return path
    }
}
