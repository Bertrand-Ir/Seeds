package app.seeds.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Curated icon set: one consistent rounded family, keyed by a stable string stored in the DB. */
object SeedIcons {
    val seedKeys = listOf(
        "laptop", "work", "code", "book", "school", "lightbulb", "psychology", "write",
        "fitness", "run", "walk", "sport", "still", "spa", "heart", "volunteer",
        "family", "groups", "call", "food", "cafe", "savings", "music", "translate",
        "language", "brush", "palette", "camera", "science", "home", "clean", "water", "sleep",
    )

    private val map: Map<String, ImageVector> = mapOf(
        "laptop" to Icons.Rounded.Laptop, "work" to Icons.Rounded.Work, "code" to Icons.Rounded.Code,
        "book" to Icons.AutoMirrored.Rounded.MenuBook, "school" to Icons.Rounded.School, "lightbulb" to Icons.Rounded.Lightbulb,
        "psychology" to Icons.Rounded.Psychology, "write" to Icons.Rounded.Edit, "fitness" to Icons.Rounded.FitnessCenter,
        "run" to Icons.AutoMirrored.Rounded.DirectionsRun, "walk" to Icons.AutoMirrored.Rounded.DirectionsWalk,
        "sport" to Icons.Rounded.SportsSoccer, "still" to Icons.Rounded.SelfImprovement, "spa" to Icons.Rounded.Spa,
        "heart" to Icons.Rounded.Favorite, "volunteer" to Icons.Rounded.VolunteerActivism, "family" to Icons.Rounded.FamilyRestroom,
        "groups" to Icons.Rounded.Groups, "call" to Icons.Rounded.Phone, "food" to Icons.Rounded.Restaurant,
        "cafe" to Icons.Rounded.LocalCafe, "savings" to Icons.Rounded.Savings, "music" to Icons.Rounded.MusicNote,
        "translate" to Icons.Rounded.Translate, "language" to Icons.Rounded.Language, "brush" to Icons.Rounded.Brush,
        "palette" to Icons.Rounded.Palette, "camera" to Icons.Rounded.CameraAlt, "science" to Icons.Rounded.Science,
        "home" to Icons.Rounded.Home, "clean" to Icons.Rounded.CleaningServices, "water" to Icons.Rounded.WaterDrop,
        "sleep" to Icons.Rounded.Bedtime,
        // money
        "bus" to Icons.Rounded.DirectionsBus, "phone" to Icons.Rounded.PhoneAndroid, "bolt" to Icons.Rounded.Bolt,
        "gift" to Icons.Rounded.CardGiftcard, "health" to Icons.Rounded.LocalHospital, "bag" to Icons.Rounded.ShoppingBag,
        "party" to Icons.Rounded.Celebration, "store" to Icons.Rounded.Storefront, "lend" to Icons.Rounded.SwapHoriz,
        "repay" to Icons.Rounded.Payments, "refund" to Icons.AutoMirrored.Rounded.Undo, "more" to Icons.Rounded.MoreHoriz,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
    )

    fun of(key: String): ImageVector = map[key] ?: Icons.Rounded.Spa
}
