package app.opaw.core

import app.opaw.core.DogSize.GIANT
import app.opaw.core.DogSize.LARGE
import app.opaw.core.DogSize.MEDIUM
import app.opaw.core.DogSize.SMALL
import app.opaw.core.EnergyLevel.HIGH
import app.opaw.core.EnergyLevel.LOW

/** A breed Opaw knows about, with typical traits used to prefill onboarding. */
data class BreedInfo(
    val name: String,
    val size: DogSize,
    val energy: EnergyLevel,
    /** Other names an image classifier or a person might use. */
    val aliases: List<String> = emptyList(),
)

/** A breed guess from a photo. */
data class BreedGuess(val breed: BreedInfo, val confidence: Double)

object BreedCatalog {
    private val MED = EnergyLevel.MEDIUM

    val all: List<BreedInfo> = listOf(
        BreedInfo("Labrador Retriever", LARGE, HIGH, listOf("labrador", "lab")),
        BreedInfo("Golden Retriever", LARGE, HIGH, listOf("golden")),
        BreedInfo("German Shepherd", LARGE, HIGH, listOf("alsatian", "german shepherd dog")),
        BreedInfo("French Bulldog", SMALL, MED, listOf("frenchie", "french bull dog")),
        BreedInfo("Bulldog", MEDIUM, LOW, listOf("english bulldog", "british bulldog")),
        BreedInfo("Poodle", MEDIUM, HIGH, listOf("standard poodle", "miniature poodle", "toy poodle")),
        BreedInfo("Beagle", MEDIUM, HIGH),
        BreedInfo("Rottweiler", LARGE, MED),
        BreedInfo("Dachshund", SMALL, MED, listOf("sausage dog", "wiener dog")),
        BreedInfo("Pembroke Welsh Corgi", SMALL, HIGH, listOf("corgi", "pembroke", "cardigan", "welsh corgi")),
        BreedInfo("Siberian Husky", LARGE, HIGH, listOf("husky", "eskimo dog")),
        BreedInfo("Alaskan Malamute", GIANT, HIGH, listOf("malamute")),
        BreedInfo("Shih Tzu", SMALL, LOW, listOf("shihtzu")),
        BreedInfo("Chihuahua", SMALL, MED),
        BreedInfo("Pomeranian", SMALL, MED),
        BreedInfo("Yorkshire Terrier", SMALL, MED, listOf("yorkie")),
        BreedInfo("Pug", SMALL, LOW),
        BreedInfo("Boxer", LARGE, HIGH),
        BreedInfo("Border Collie", MEDIUM, HIGH, listOf("collie")),
        BreedInfo("Australian Shepherd", MEDIUM, HIGH, listOf("aussie")),
        BreedInfo("Shetland Sheepdog", SMALL, HIGH, listOf("sheltie")),
        BreedInfo("Doberman Pinscher", LARGE, HIGH, listOf("doberman")),
        BreedInfo("Great Dane", GIANT, MED),
        BreedInfo("Saint Bernard", GIANT, LOW, listOf("st bernard")),
        BreedInfo("Bernese Mountain Dog", GIANT, MED, listOf("bernese")),
        BreedInfo("Newfoundland", GIANT, LOW),
        BreedInfo("Cavalier King Charles Spaniel", SMALL, MED, listOf("cavalier", "blenheim spaniel", "king charles spaniel")),
        BreedInfo("Cocker Spaniel", MEDIUM, MED, listOf("english cocker spaniel", "american cocker spaniel")),
        BreedInfo("Shiba Inu", SMALL, MED, listOf("shiba")),
        BreedInfo("Akita", LARGE, MED),
        BreedInfo("Samoyed", LARGE, HIGH),
        BreedInfo("Maltese", SMALL, LOW, listOf("maltese dog")),
        BreedInfo("Bichon Frise", SMALL, MED, listOf("bichon")),
        BreedInfo("Miniature Schnauzer", SMALL, MED, listOf("schnauzer", "standard schnauzer", "giant schnauzer")),
        BreedInfo("Boston Terrier", SMALL, MED),
        BreedInfo("Jack Russell Terrier", SMALL, HIGH, listOf("jack russell", "parson russell terrier")),
        BreedInfo("West Highland White Terrier", SMALL, MED, listOf("westie", "west highland terrier")),
        BreedInfo("Bull Terrier", MEDIUM, HIGH),
        BreedInfo("American Staffordshire Terrier", MEDIUM, HIGH, listOf("staffordshire bullterrier", "staffordshire bull terrier", "pit bull", "pitbull", "american pit bull terrier")),
        BreedInfo("Dalmatian", LARGE, HIGH, listOf("coach dog")),
        BreedInfo("Weimaraner", LARGE, HIGH),
        BreedInfo("Vizsla", MEDIUM, HIGH, listOf("hungarian pointer")),
        BreedInfo("German Shorthaired Pointer", LARGE, HIGH, listOf("pointer")),
        BreedInfo("Basset Hound", MEDIUM, LOW, listOf("basset")),
        BreedInfo("Bloodhound", LARGE, MED),
        BreedInfo("Greyhound", LARGE, MED),
        BreedInfo("Whippet", MEDIUM, MED),
        BreedInfo("Italian Greyhound", SMALL, MED),
        BreedInfo("Chow Chow", MEDIUM, LOW, listOf("chow")),
        BreedInfo("Shar Pei", MEDIUM, LOW),
        BreedInfo("Lhasa Apso", SMALL, LOW, listOf("lhasa")),
        BreedInfo("Papillon", SMALL, HIGH),
        BreedInfo("Havanese", SMALL, MED),
        BreedInfo("Belgian Malinois", LARGE, HIGH, listOf("malinois", "groenendael")),
        BreedInfo("Rhodesian Ridgeback", LARGE, HIGH, listOf("ridgeback")),
        BreedInfo("Great Pyrenees", GIANT, LOW, listOf("pyrenean mountain dog")),
        BreedInfo("Mastiff", GIANT, LOW, listOf("bull mastiff", "bullmastiff", "tibetan mastiff", "english mastiff")),
        BreedInfo("Irish Setter", LARGE, HIGH, listOf("red setter", "english setter", "gordon setter")),
        BreedInfo("Aspin", MEDIUM, MED, listOf("asong pinoy")),
    )

    /** Finds a breed by name or alias, ignoring case, spaces, hyphens and underscores. */
    fun lookup(text: String): BreedInfo? {
        val key = normalize(text)
        if (key.isEmpty()) return null
        return all.firstOrNull { breed ->
            normalize(breed.name) == key || breed.aliases.any { normalize(it) == key }
        }
    }

    /** Breeds whose name starts with or contains the text, for suggestions while typing. */
    fun suggestions(text: String, limit: Int = 5): List<BreedInfo> {
        val key = normalize(text)
        if (key.isEmpty()) return emptyList()
        val prefix = all.filter { normalize(it.name).startsWith(key) }
        val contains = all.filter { !normalize(it.name).startsWith(key) && normalize(it.name).contains(key) }
        return (prefix + contains).take(limit)
    }

    /**
     * Picks the most likely breed from image classifier labels and their confidences.
     *
     * Labels look like "golden_retriever" or "Labrador retriever". Unknown labels are ignored,
     * and guesses under [minimumConfidence] are dropped.
     */
    fun bestGuess(labels: List<Pair<String, Double>>, minimumConfidence: Double = 0.08): BreedGuess? {
        var best: BreedGuess? = null
        for ((identifier, confidence) in labels) {
            if (confidence < minimumConfidence) continue
            val breed = lookup(identifier) ?: continue
            if (best == null || confidence > best.confidence) best = BreedGuess(breed, confidence)
        }
        return best
    }

    internal fun normalize(text: String): String =
        text.lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(".", "")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
