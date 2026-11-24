import AVFoundation
import CoreImage
import SwiftUI
import UIKit

/// Plays a looping "stacked alpha" MP4: colour in the top half, transparency mask in the bottom half.
///
/// This gives a transparent, realistic video of the dog that can sit on any background,
/// using only standard H.264 files that every iPhone decodes in hardware.
struct StackedAlphaVideoView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> StackedAlphaPlayerView {
        let view = StackedAlphaPlayerView()
        view.load(url)
        return view
    }

    func updateUIView(_ view: StackedAlphaPlayerView, context: Context) {
        view.load(url)
    }

    static func dismantleUIView(_ view: StackedAlphaPlayerView, coordinator: ()) {
        view.stop()
    }
}

final class StackedAlphaPlayerView: UIView {
    private let player = AVPlayer()
    private var output: AVPlayerItemVideoOutput?
    private var displayLink: CADisplayLink?
    private var currentURL: URL?
    private var endObserver: NSObjectProtocol?
    private static let ciContext = CIContext(options: [.cacheIntermediates: false])

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        layer.contentsGravity = .resizeAspect
        player.isMuted = true
        player.actionAtItemEnd = .none
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func load(_ url: URL) {
        guard url != currentURL else { return }
        currentURL = url
        let item = AVPlayerItem(url: url)
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
        ])
        item.add(output)
        self.output = output
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            self?.player.seek(to: .zero)
            self?.player.play()
        }
        player.replaceCurrentItem(with: item)
        player.play()
        startDisplayLink()
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        player.pause()
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = nil
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            displayLink?.isPaused = true
            player.pause()
        } else {
            displayLink?.isPaused = false
            player.play()
        }
    }

    private func startDisplayLink() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(step))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 15, maximum: 30, preferred: 30)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    @objc private func step(_ link: CADisplayLink) {
        guard let output else { return }
        let time = output.itemTime(forHostTime: CACurrentMediaTime())
        guard output.hasNewPixelBuffer(forItemTime: time),
              let buffer = output.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: nil) else { return }
        if let image = Self.composite(CIImage(cvPixelBuffer: buffer)) {
            layer.contents = image
        }
    }

    /// Splits the frame into colour (top) and mask (bottom) and blends them into one transparent image.
    static func composite(_ frame: CIImage) -> CGImage? {
        let extent = frame.extent
        let half = (extent.height / 2).rounded(.down)
        // Core Image's origin is bottom-left, so the top half of the video has the higher y values.
        let colorRect = CGRect(x: extent.minX, y: extent.minY + half, width: extent.width, height: half)
        let maskRect = CGRect(x: extent.minX, y: extent.minY, width: extent.width, height: half)
        let color = frame.cropped(to: colorRect).transformed(by: CGAffineTransform(translationX: 0, y: -half))
        let mask = frame.cropped(to: maskRect)
        let clear = CIImage(color: .clear).cropped(to: maskRect)
        let blended = color.applyingFilter("CIBlendWithMask", parameters: [
            kCIInputBackgroundImageKey: clear,
            kCIInputMaskImageKey: mask
        ])
        return ciContext.createCGImage(blended, from: maskRect)
    }
}
