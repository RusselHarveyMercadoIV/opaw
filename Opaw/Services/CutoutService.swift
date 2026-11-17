import CoreImage
import OpawKit
import UIKit
import Vision

enum CutoutError: LocalizedError {
    case unreadableImage
    case noSubject
    case renderFailed

    var errorDescription: String? {
        switch self {
        case .unreadableImage: return "That photo couldn't be read."
        case .noSubject: return "We couldn't find your dog in this photo. Try one where your dog fills more of the frame."
        case .renderFailed: return "Something went wrong cutting out the photo."
        }
    }
}

/// Finds the dog in a photo and lifts it off the background, entirely on device.
enum CutoutService {
    /// Whether Vision recognizes a dog in the photo.
    static func containsDog(_ image: UIImage) async -> Bool {
        guard let cgImage = image.cgImage else { return false }
        return await Task.detached(priority: .userInitiated) {
            let request = VNRecognizeAnimalsRequest()
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                return false
            }
            let observations = request.results ?? []
            return observations.contains { observation in
                observation.labels.contains { $0.identifier == VNAnimalIdentifier.dog.rawValue && $0.confidence > 0.3 }
            }
        }.value
    }

    /// Guesses the breed with Vision's built-in image classifier, entirely on device.
    ///
    /// Returns nil when no known breed is confident enough; the person can always type one.
    static func guessBreed(_ image: UIImage) async -> BreedGuess? {
        guard let cgImage = image.cgImage else { return nil }
        return await Task.detached(priority: .userInitiated) {
            let request = VNClassifyImageRequest()
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                return nil
            }
            let labels = (request.results ?? []).map { (identifier: $0.identifier, confidence: Double($0.confidence)) }
            return BreedCatalog.bestGuess(from: labels)
        }.value
    }

    /// Returns the foreground subject on a transparent background, cropped to the subject.
    static func cutout(_ image: UIImage) async throws -> UIImage {
        guard let cgImage = image.cgImage else { throw CutoutError.unreadableImage }
        let output: CGImage = try await Task.detached(priority: .userInitiated) {
            let request = VNGenerateForegroundInstanceMaskRequest()
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            try handler.perform([request])
            guard let result = request.results?.first, !result.allInstances.isEmpty else {
                throw CutoutError.noSubject
            }
            let buffer = try result.generateMaskedImage(
                ofInstances: result.allInstances,
                from: handler,
                croppedToInstancesExtent: true
            )
            let ciImage = CIImage(cvPixelBuffer: buffer)
            guard let rendered = CIContext().createCGImage(ciImage, from: ciImage.extent) else {
                throw CutoutError.renderFailed
            }
            return rendered
        }.value
        return UIImage(cgImage: output)
    }
}

extension UIImage {
    /// Redraws the image upright and no larger than `maxDimension` points on its longest side.
    func preparedForProcessing(maxDimension: CGFloat = 1600) -> UIImage {
        let longest = max(size.width, size.height)
        let scale = longest > maxDimension ? maxDimension / longest : 1
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}
