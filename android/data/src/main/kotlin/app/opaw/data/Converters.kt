package app.opaw.data

import androidx.room.TypeConverter
import app.opaw.core.GeoPoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())
    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

/** The JSON shape of a route point, kept here so `core` stays free of serialization. */
@Serializable
private data class GeoPointJson(
    val latitude: Double,
    val longitude: Double,
    @Serializable(with = InstantSerializer::class) val timestamp: Instant,
    val horizontalAccuracy: Double,
)

internal val json = Json { ignoreUnknownKeys = true }

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun intsToString(value: List<Int>): String = json.encodeToString(ListSerializer(Int.serializer()), value)
    @TypeConverter fun stringToInts(value: String): List<Int> = json.decodeFromString(ListSerializer(Int.serializer()), value)

    @TypeConverter fun stringsToString(value: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), value)
    @TypeConverter fun stringToStrings(value: String): List<String> = json.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter fun relativesToString(value: List<Relative>): String = json.encodeToString(ListSerializer(Relative.serializer()), value)
    @TypeConverter fun stringToRelatives(value: String): List<Relative> = json.decodeFromString(ListSerializer(Relative.serializer()), value)

    @TypeConverter fun pinsToString(value: List<WalkPin>): String = json.encodeToString(ListSerializer(WalkPin.serializer()), value)
    @TypeConverter fun stringToPins(value: String): List<WalkPin> = json.decodeFromString(ListSerializer(WalkPin.serializer()), value)

    @TypeConverter fun routeToString(value: List<GeoPoint>): String = json.encodeToString(
        ListSerializer(GeoPointJson.serializer()),
        value.map { GeoPointJson(it.latitude, it.longitude, it.timestamp, it.horizontalAccuracy) },
    )

    @TypeConverter fun stringToRoute(value: String): List<GeoPoint> =
        json.decodeFromString(ListSerializer(GeoPointJson.serializer()), value)
            .map { GeoPoint(it.latitude, it.longitude, it.timestamp, it.horizontalAccuracy) }
}
