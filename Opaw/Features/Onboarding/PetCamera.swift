import AVFoundation
import SwiftUI

/// A live camera for the onboarding photo (4b): preview, shutter and flip.
/// On a device without a camera, such as the simulator, `isAvailable` stays false.
@MainActor
@Observable
final class PetCamera: NSObject {
    let session = AVCaptureSession()
    private(set) var isAvailable = false
    private(set) var isRunning = false

    @ObservationIgnored private let output = AVCapturePhotoOutput()
    @ObservationIgnored private var input: AVCaptureDeviceInput?
    @ObservationIgnored private var position: AVCaptureDevice.Position = .back
    @ObservationIgnored private var pending: CheckedContinuation<UIImage?, Never>?
    @ObservationIgnored private let queue = DispatchQueue(label: "app.opaw.camera")

    func start() async {
        guard AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil else { return }
        var allowed = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        if AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined {
            allowed = await AVCaptureDevice.requestAccess(for: .video)
        }
        guard allowed else { return }
        if input == nil { configure() }
        guard input != nil else { return }
        isAvailable = true
        let session = session
        await withCheckedContinuation { (done: CheckedContinuation<Void, Never>) in
            queue.async {
                if !session.isRunning { session.startRunning() }
                done.resume()
            }
        }
        isRunning = true
    }

    func stop() {
        let session = session
        queue.async { if session.isRunning { session.stopRunning() } }
        isRunning = false
    }

    func flip() {
        guard isAvailable else { return }
        position = position == .back ? .front : .back
        configure()
    }

    func capture() async -> UIImage? {
        guard isAvailable, pending == nil else { return nil }
        return await withCheckedContinuation { continuation in
            pending = continuation
            output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }
    }

    private func configure() {
        session.beginConfiguration()
        session.sessionPreset = .photo
        if let input { session.removeInput(input) }
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
           let newInput = try? AVCaptureDeviceInput(device: device),
           session.canAddInput(newInput) {
            session.addInput(newInput)
            input = newInput
        }
        if !session.outputs.contains(output), session.canAddOutput(output) {
            session.addOutput(output)
        }
        session.commitConfiguration()
    }

    fileprivate func finish(_ image: UIImage?) {
        pending?.resume(returning: image)
        pending = nil
    }
}

extension PetCamera: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let image = photo.fileDataRepresentation().flatMap(UIImage.init(data:))
        Task { @MainActor in self.finish(image) }
    }
}

/// The camera's live picture, filling its frame.
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}
