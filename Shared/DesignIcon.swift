import OpawKit
import SwiftUI

/// The icons from the Pet Avatar App v2 design, drawn from their original 24×24 SVG paths.
enum DesignIcon: String, CaseIterable {
    case back, chevronRight, plus, check, home, paw, activity, photo
    case heart, share, upload, bell, calendar, trophy, camera, sparkle, document, pause
    case meal, water, pee, poop, meds, bath, brush, nails, weight, vet
    case close, chevronDown, refresh, link, location, lock, chart, users, cloud, help, grid, ruler, clock, pets, settings, wave

    /// How the icon is painted: an outline, or solid shapes.
    enum Style { case stroke, fill }

    fileprivate enum Element {
        case path(String)
        case circle(CGFloat, CGFloat, CGFloat)
        case rect(CGFloat, CGFloat, CGFloat, CGFloat, CGFloat)
        /// Drawn in the background color on top of a filled icon (the photo icon's sun and hill).
        case cutout(String)
        case cutoutCircle(CGFloat, CGFloat, CGFloat)
        /// An outline inside a filled icon (the activity line, the share links).
        case strokedPath(String, CGFloat)
    }

    var style: Style {
        switch self {
        case .home, .paw, .activity, .photo, .heart, .share, .pause: return .fill
        default: return .stroke
        }
    }

    /// The design's stroke width for this icon, in its 24-point box.
    var defaultWeight: CGFloat {
        switch self {
        case .check: return 4
        case .chevronRight: return 3
        case .meal, .water, .pee, .poop, .meds, .bath, .brush, .nails, .weight, .vet: return 2.3
        case .calendar, .camera, .document: return 2.4
        case .refresh, .link, .location, .chart, .users, .cloud, .help, .grid, .ruler, .pets, .settings, .wave: return 2.4
        case .sparkle: return 2.2
        default: return 2.6
        }
    }

    fileprivate var elements: [Element] {
        switch self {
        case .back: return [.path("m15 18-6-6 6-6")]
        case .chevronRight: return [.path("m9 18 6-6-6-6")]
        case .plus: return [.path("M5 12h14M12 5v14")]
        case .check: return [.path("m5 12 5 5 9-10")]
        case .home: return [.path("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")]
        case .paw:
            return [
                .circle(5, 10, 2.3), .circle(9, 5.5, 2.3), .circle(15, 5.5, 2.3), .circle(19, 10, 2.3),
                .path("M12 11c-3 0-6 4.5-6 7 0 2 1.5 3 3 3 1.2 0 2-.6 3-.6s1.8.6 3 .6c1.5 0 3-1 3-3 0-2.5-3-7-6-7Z")
            ]
        case .activity: return [.strokedPath("M22 12h-4l-3 9L9 3l-3 9H2", 2.6)]
        case .photo:
            return [.rect(3, 3, 18, 18, 5), .cutoutCircle(9, 9, 2), .cutout("m21 15-4-4-10 10h11a3 3 0 0 0 3-3z")]
        case .heart:
            return [.path("M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z")]
        case .share:
            return [.circle(18, 5, 2.5), .circle(6, 12, 2.5), .circle(18, 19, 2.5), .strokedPath("m8.6 13.5 6.8 4M15.4 6.5l-6.8 4", 2.4)]
        case .upload: return [.path("M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13")]
        case .bell: return [.path("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"), .path("M10.3 21a1.94 1.94 0 0 0 3.4 0")]
        case .calendar: return [.rect(3, 4, 18, 18, 4), .path("M16 2v4M8 2v4M3 10h18")]
        case .trophy: return [.path("M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0z")]
        case .camera:
            return [.path("M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z"), .circle(12, 13, 3)]
        case .sparkle: return [.path("M12 3l1.9 5.8L20 10l-6.1 1.2L12 17l-1.9-5.8L4 10l6.1-1.2z")]
        case .document: return [.path("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"), .path("M14 2v6h6M8 13h8M8 17h5")]
        case .pause: return [.rect(6, 5, 4, 14, 1.5), .rect(14, 5, 4, 14, 1.5)]
        case .meal: return [.path("M3 11h18a9 9 0 0 1-18 0Z"), .path("M7 21h10M9 7c0-2 2-2 2-4M14 7c0-2 2-2 2-4")]
        case .water: return [.path("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z")]
        case .pee:
            return [
                .path("M7 16.3c2.2 0 4-1.83 4-4.05 0-1.16-.57-2.26-1.71-3.19S7.29 6.75 7 5.3c-.29 1.45-1.14 2.84-2.29 3.76S3 11.1 3 12.25c0 2.22 1.8 4.05 4 4.05z"),
                .path("M12.56 6.6A10.97 10.97 0 0 0 14 3.02c.5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a6.98 6.98 0 0 1-11.91 4.97")
            ]
        case .poop: return [.path("M5 20h14a3 3 0 0 0 .6-5.94A3 3 0 0 0 16 10h-.5A3 3 0 0 0 13 4c0 1.5-1.5 2.5-3 2.5A3 3 0 0 0 8 11.8 3 3 0 0 0 5 20Z")]
        case .meds: return [.path("m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z"), .path("m8.5 8.5 7 7")]
        case .bath:
            return [.path("M9 6 6.5 3.5a1.5 1.5 0 0 0-1-.5C4.68 3 4 3.68 4 4.5V17a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5"), .path("M2 12h20M7 19v2M17 19v2")]
        case .brush: return [.path("M3 7h18v4H3z"), .path("M5 11v7M9 11v7M13 11v7M17 11v7")]
        case .nails: return [.circle(6, 6, 3), .circle(6, 18, 3), .path("M20 4 8.12 15.88M14.47 14.48 20 20M8.12 8.12 12 12")]
        case .weight:
            return [.circle(12, 5, 3), .path("M6.5 8a2 2 0 0 0-1.9 1.46L2.1 18.5A2 2 0 0 0 4 21h16a2 2 0 0 0 1.93-2.54L19.4 9.5A2 2 0 0 0 17.48 8Z")]
        case .vet:
            return [.path("M11 2v2M5 2v2M5 3H4a2 2 0 0 0-2 2v4a6 6 0 0 0 12 0V5a2 2 0 0 0-2-2h-1"), .path("M8 15a6 6 0 0 0 12 0v-3"), .circle(20, 10, 2)]
        case .close: return [.path("M18 6 6 18M6 6l12 12")]
        case .chevronDown: return [.path("m6 9 6 6 6-6")]
        case .refresh:
            return [.path("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"), .path("M21 3v5h-5"), .path("M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"), .path("M8 16H3v5")]
        case .link:
            return [.path("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"), .path("M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")]
        case .location: return [.path("M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"), .circle(12, 10, 3)]
        case .lock: return [.rect(4, 11, 16, 10, 3), .path("M8 11V7a4 4 0 0 1 8 0v4")]
        case .chart: return [.path("M3 3v18h18"), .path("M8 16v-4M13 16V8M18 16v-7")]
        case .users: return [.path("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"), .circle(9, 7, 4), .path("M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75")]
        case .cloud: return [.path("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")]
        case .help: return [.circle(12, 12, 9), .path("M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3M12 17h.01")]
        case .grid: return [.rect(3, 3, 7, 7, 2), .rect(14, 3, 7, 7, 2), .rect(14, 14, 7, 7, 2), .rect(3, 14, 7, 7, 2)]
        case .ruler:
            return [.path("M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.4 2.4 0 0 1 0-3.4l2.6-2.6a2.4 2.4 0 0 1 3.4 0Z"), .path("m7.5 10.5 2-2M10.5 13.5l2-2M13.5 16.5l2-2")]
        case .clock: return [.circle(12, 12, 9), .path("M12 7v5l3 2")]
        case .wave:
            return [.path("M18 11V6a2 2 0 0 0-4 0M14 10V4a2 2 0 0 0-4 0v2M10 10.5V6a2 2 0 0 0-4 0v8"), .path("M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")]
        case .settings:
            return [.path("M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"), .circle(12, 12, 3)]
        case .pets:
            return [.circle(5, 10, 2), .circle(9, 5.5, 2), .circle(15, 5.5, 2), .circle(19, 10, 2), .path("M12 11c-3 0-6 4.5-6 7 0 2 1.5 3 3 3 1.2 0 2-.6 3-.6s1.8.6 3 .6c1.5 0 3-1 3-3 0-2.5-3-7-6-7Z")]
        }
    }

    /// The design icon for an SF Symbol name used in the app, if the design has one.
    init?(symbol: String) {
        switch symbol {
        case "chevron.left": self = .back
        case "chevron.right": self = .chevronRight
        case "plus": self = .plus
        case "checkmark": self = .check
        case "house.fill": self = .home
        case "pawprint.fill": self = .paw
        case "waveform.path.ecg": self = .activity
        case "photo.fill": self = .photo
        case "heart.fill": self = .heart
        case "point.3.filled.connected.trianglepath.dotted": self = .share
        case "square.and.arrow.up": self = .upload
        case "bell.fill", "bell": self = .bell
        case "calendar": self = .calendar
        case "trophy", "trophy.fill": self = .trophy
        case "camera", "camera.fill": self = .camera
        case "sparkle", "sparkles": self = .sparkle
        case "doc.text": self = .document
        case "pause.fill": self = .pause
        default:
            if let kind = CareKind.allCases.first(where: { $0.symbol == symbol }), let icon = DesignIcon(kind: kind) {
                self = icon
            } else {
                return nil
            }
        }
    }

    init?(kind: CareKind) {
        switch kind {
        case .meal: self = .meal
        case .water: self = .water
        case .pee: self = .pee
        case .poop: self = .poop
        case .meds: self = .meds
        case .bath: self = .bath
        case .brush: self = .brush
        case .nails: self = .nails
        case .weight: self = .weight
        case .vet: self = .vet
        case .play: return nil
        }
    }

    /// The icon's main shapes, in a 24×24 box.
    fileprivate var body: Path {
        var path = Path()
        for element in elements {
            switch element {
            case .path(let d): path.addPath(SVGPath.parse(d))
            case .circle(let x, let y, let r): path.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            case .rect(let x, let y, let w, let h, let r):
                path.addRoundedRect(in: CGRect(x: x, y: y, width: w, height: h), cornerSize: CGSize(width: r, height: r))
            case .cutout, .cutoutCircle, .strokedPath: break
            }
        }
        return path
    }

    fileprivate var cutouts: Path {
        var path = Path()
        for element in elements {
            switch element {
            case .cutout(let d): path.addPath(SVGPath.parse(d))
            case .cutoutCircle(let x, let y, let r): path.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            default: break
            }
        }
        return path
    }

    fileprivate var strokedParts: [(Path, CGFloat)] {
        elements.compactMap {
            if case .strokedPath(let d, let width) = $0 { return (SVGPath.parse(d), width) }
            return nil
        }
    }
}

/// Draws a design icon at any size, tinted with the current foreground style.
struct Icon: View {
    let icon: DesignIcon
    var size: CGFloat = 22
    /// Stroke width in the icon's 24-point box; defaults to the design's.
    var weight: CGFloat?
    /// Fill for the cut-out details of solid icons (the photo icon).
    var cutoutColor: Color = .white

    init(_ icon: DesignIcon, size: CGFloat = 22, weight: CGFloat? = nil, cutoutColor: Color = .white) {
        self.icon = icon
        self.size = size
        self.weight = weight
        self.cutoutColor = cutoutColor
    }

    var body: some View {
        let scale = size / 24
        let transform = CGAffineTransform(scaleX: scale, y: scale)
        let line = (weight ?? icon.defaultWeight) * scale
        ZStack {
            switch icon.style {
            case .stroke:
                IconShape(path: icon.body.applying(transform))
                    .stroke(style: StrokeStyle(lineWidth: line, lineCap: .round, lineJoin: .round))
            case .fill:
                IconShape(path: icon.body.applying(transform)).fill()
                ForEach(Array(icon.strokedParts.enumerated()), id: \.offset) { _, part in
                    IconShape(path: part.0.applying(transform))
                        .stroke(style: StrokeStyle(lineWidth: part.1 * scale, lineCap: .round, lineJoin: .round))
                }
                IconShape(path: icon.cutouts.applying(transform)).fill(cutoutColor)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

private struct IconShape: Shape {
    let path: Path
    func path(in rect: CGRect) -> Path { path }
}

// MARK: - SVG path data

/// A small parser for SVG path data: M L H V C S Q T A Z, absolute and relative.
enum SVGPath {
    static func parse(_ d: String) -> Path {
        var path = Path()
        var tokens = Tokenizer(d)
        var current = CGPoint.zero
        var start = CGPoint.zero
        var lastControl: CGPoint?
        var lastQuad: CGPoint?
        var command: Character = "M"

        while let next = tokens.peekCommand() ?? (tokens.hasNumber ? command : nil) {
            if tokens.peekCommand() != nil { tokens.consumeCommand() }
            command = next
            let relative = command.isLowercase
            func point() -> CGPoint {
                let x = tokens.number(), y = tokens.number()
                return relative ? CGPoint(x: current.x + x, y: current.y + y) : CGPoint(x: x, y: y)
            }
            switch command.uppercased().first! {
            case "M":
                current = point()
                start = current
                path.move(to: current)
                // Extra pairs after a move are lines.
                command = relative ? "l" : "L"
                lastControl = nil; lastQuad = nil
            case "L":
                current = point()
                path.addLine(to: current)
                lastControl = nil; lastQuad = nil
            case "H":
                let x = tokens.number()
                current = CGPoint(x: relative ? current.x + x : x, y: current.y)
                path.addLine(to: current)
                lastControl = nil; lastQuad = nil
            case "V":
                let y = tokens.number()
                current = CGPoint(x: current.x, y: relative ? current.y + y : y)
                path.addLine(to: current)
                lastControl = nil; lastQuad = nil
            case "C":
                let c1 = point(), c2 = point(), end = point()
                path.addCurve(to: end, control1: c1, control2: c2)
                lastControl = c2; lastQuad = nil
                current = end
            case "S":
                let c1 = lastControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let c2 = point(), end = point()
                path.addCurve(to: end, control1: c1, control2: c2)
                lastControl = c2; lastQuad = nil
                current = end
            case "Q":
                let c = point(), end = point()
                path.addQuadCurve(to: end, control: c)
                lastQuad = c; lastControl = nil
                current = end
            case "T":
                let c = lastQuad.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let end = point()
                path.addQuadCurve(to: end, control: c)
                lastQuad = c; lastControl = nil
                current = end
            case "A":
                let rx = tokens.number(), ry = tokens.number(), rotation = tokens.number()
                let largeArc = tokens.flag(), sweep = tokens.flag()
                let end = point()
                addArc(to: &path, from: current, to: end, rx: rx, ry: ry, rotation: rotation, largeArc: largeArc, sweep: sweep)
                current = end
                lastControl = nil; lastQuad = nil
            case "Z":
                path.closeSubpath()
                current = start
                lastControl = nil; lastQuad = nil
            default:
                return path
            }
        }
        return path
    }

    /// Converts an SVG elliptical arc to cubic curves (SVG spec, appendix F.6).
    private static func addArc(to path: inout Path, from p0: CGPoint, to p1: CGPoint, rx: CGFloat, ry: CGFloat, rotation: CGFloat, largeArc: Bool, sweep: Bool) {
        var rx = abs(rx), ry = abs(ry)
        guard rx > 0, ry > 0, p0 != p1 else {
            path.addLine(to: p1)
            return
        }
        let phi = rotation * .pi / 180
        let cosPhi = cos(phi), sinPhi = sin(phi)
        let dx = (p0.x - p1.x) / 2, dy = (p0.y - p1.y) / 2
        let x1p = cosPhi * dx + sinPhi * dy
        let y1p = -sinPhi * dx + cosPhi * dy
        let lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if lambda > 1 {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        let numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        let denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var coefficient = sqrt(max(0, numerator / denominator))
        if largeArc == sweep { coefficient = -coefficient }
        let cxp = coefficient * rx * y1p / ry
        let cyp = -coefficient * ry * x1p / rx
        let cx = cosPhi * cxp - sinPhi * cyp + (p0.x + p1.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (p0.y + p1.y) / 2

        func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let dot = ux * vx + uy * vy
            let length = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var value = acos(max(-1, min(1, dot / length)))
            if ux * vy - uy * vx < 0 { value = -value }
            return value
        }
        let theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if !sweep && delta > 0 { delta -= 2 * .pi }
        if sweep && delta < 0 { delta += 2 * .pi }

        let segments = max(1, Int(ceil(abs(delta) / (.pi / 2))))
        let step = delta / CGFloat(segments)
        let k = 4 / 3 * tan(step / 4)
        func pointOnEllipse(_ t: CGFloat) -> CGPoint {
            CGPoint(x: cx + rx * cos(t) * cosPhi - ry * sin(t) * sinPhi, y: cy + rx * cos(t) * sinPhi + ry * sin(t) * cosPhi)
        }
        func derivative(_ t: CGFloat) -> CGPoint {
            CGPoint(x: -rx * sin(t) * cosPhi - ry * cos(t) * sinPhi, y: -rx * sin(t) * sinPhi + ry * cos(t) * cosPhi)
        }
        var t = theta1
        for index in 0..<segments {
            let t2 = t + step
            let a = pointOnEllipse(t), b = pointOnEllipse(t2)
            let da = derivative(t), db = derivative(t2)
            let end = index == segments - 1 ? p1 : b
            path.addCurve(
                to: end,
                control1: CGPoint(x: a.x + k * da.x, y: a.y + k * da.y),
                control2: CGPoint(x: b.x - k * db.x, y: b.y - k * db.y)
            )
            t = t2
        }
    }

    private struct Tokenizer {
        let chars: [Character]
        var index = 0

        init(_ string: String) { chars = Array(string) }

        mutating func skipSeparators() {
            while index < chars.count, chars[index] == " " || chars[index] == "," || chars[index] == "\n" || chars[index] == "\t" { index += 1 }
        }

        mutating func peekCommand() -> Character? {
            skipSeparators()
            guard index < chars.count else { return nil }
            let c = chars[index]
            return c.isLetter && c != "e" && c != "E" ? c : nil
        }

        mutating func consumeCommand() { index += 1 }

        var hasNumber: Bool {
            mutating get {
                skipSeparators()
                guard index < chars.count else { return false }
                let c = chars[index]
                return c.isNumber || c == "-" || c == "." || c == "+"
            }
        }

        /// Arc flags can be written without separators ("a1 1 0 01..."): read a single 0 or 1.
        mutating func flag() -> Bool {
            skipSeparators()
            guard index < chars.count else { return false }
            let value = chars[index] == "1"
            index += 1
            return value
        }

        mutating func number() -> CGFloat {
            skipSeparators()
            var text = ""
            var seenDot = false
            var seenExponent = false
            if index < chars.count, chars[index] == "-" || chars[index] == "+" {
                text.append(chars[index]); index += 1
            }
            while index < chars.count {
                let c = chars[index]
                if c.isNumber {
                    text.append(c)
                } else if c == "." && !seenDot && !seenExponent {
                    seenDot = true; text.append(c)
                } else if (c == "e" || c == "E") && !seenExponent {
                    seenExponent = true; text.append(c)
                    if index + 1 < chars.count, chars[index + 1] == "-" || chars[index + 1] == "+" {
                        index += 1; text.append(chars[index])
                    }
                } else {
                    break
                }
                index += 1
            }
            return CGFloat(Double(text) ?? 0)
        }
    }
}

/// Draws the design's icon for an SF Symbol name when the design has one, and the SF Symbol otherwise.
struct SymbolIcon: View {
    let symbol: String
    var size: CGFloat = 22
    var cutoutColor: Color = .white

    var body: some View {
        if let icon = DesignIcon(symbol: symbol) {
            Icon(icon, size: size, cutoutColor: cutoutColor)
        } else {
            Image(systemName: symbol)
                .font(.system(size: size * 0.85, weight: .bold))
                .frame(width: size, height: size)
        }
    }
}
