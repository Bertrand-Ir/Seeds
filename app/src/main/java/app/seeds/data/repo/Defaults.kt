package app.seeds.data.repo

import app.seeds.data.db.Category
import app.seeds.data.db.Category.Special
import app.seeds.data.db.Direction
import app.seeds.data.db.Period
import app.seeds.data.db.Seed

object Defaults {
    /** Suggestions offered during onboarding — the user picks, renames and sizes them. */
    val seedIdeas = listOf(
        Seed(name = "Deep work", icon = "laptop", color = 0, targetMinutes = 240, period = Period.DAILY, focus = true, activeDays = Seed.WEEKDAYS),
        Seed(name = "Exercise", icon = "fitness", color = 3, targetMinutes = 45, period = Period.DAILY),
        Seed(name = "Reading", icon = "book", color = 4, targetMinutes = 30, period = Period.DAILY),
        Seed(name = "Prayer & stillness", icon = "still", color = 5, targetMinutes = 20, period = Period.DAILY),
        Seed(name = "Learning a skill", icon = "school", color = 1, targetMinutes = 300, period = Period.WEEKLY, focus = true),
        Seed(name = "Side project", icon = "code", color = 2, targetMinutes = 360, period = Period.WEEKLY, focus = true),
        Seed(name = "Family time", icon = "family", color = 6, targetMinutes = 180, period = Period.WEEKLY),
        Seed(name = "Money review", icon = "savings", color = 7, targetMinutes = 60, period = Period.MONTHLY),
    )

    val categories: List<Category> = listOf(
        // Money out
        Category(name = "Food & groceries", icon = "food", color = 2, direction = Direction.OUT, sortOrder = 0),
        Category(name = "Transport", icon = "bus", color = 1, direction = Direction.OUT, sortOrder = 1),
        Category(name = "Airtime & data", icon = "phone", color = 4, direction = Direction.OUT, sortOrder = 2),
        Category(name = "Rent & home", icon = "home", color = 7, direction = Direction.OUT, sortOrder = 3),
        Category(name = "Bills & utilities", icon = "bolt", color = 2, direction = Direction.OUT, sortOrder = 4),
        Category(name = "Family & gifts", icon = "gift", color = 6, direction = Direction.OUT, sortOrder = 5),
        Category(name = "Health", icon = "health", color = 3, direction = Direction.OUT, sortOrder = 6),
        Category(name = "Education", icon = "school", color = 1, direction = Direction.OUT, sortOrder = 7),
        Category(name = "Shopping", icon = "bag", color = 6, direction = Direction.OUT, sortOrder = 8),
        Category(name = "Fun & going out", icon = "party", color = 4, direction = Direction.OUT, sortOrder = 9),
        Category(name = "Savings", icon = "savings", color = 0, direction = Direction.OUT, sortOrder = 10),
        Category(name = "Business costs", icon = "store", color = 5, direction = Direction.OUT, sortOrder = 11),
        Category(name = "Lent to someone", icon = "lend", color = 3, direction = Direction.OUT, special = Special.LOAN_GIVEN, sortOrder = 12),
        Category(name = "Paid back a debt", icon = "repay", color = 7, direction = Direction.OUT, special = Special.REPAY_SENT, sortOrder = 13),
        Category(name = "Other", icon = "more", color = 7, direction = Direction.OUT, sortOrder = 14),
        // Money in
        Category(name = "Salary", icon = "work", color = 0, direction = Direction.IN, sortOrder = 0),
        Category(name = "Business income", icon = "store", color = 5, direction = Direction.IN, sortOrder = 1),
        Category(name = "Gift received", icon = "gift", color = 6, direction = Direction.IN, sortOrder = 2),
        Category(name = "Borrowed", icon = "lend", color = 3, direction = Direction.IN, special = Special.LOAN_TAKEN, sortOrder = 3),
        Category(name = "Paid back to me", icon = "repay", color = 1, direction = Direction.IN, special = Special.REPAY_RECEIVED, sortOrder = 4),
        Category(name = "Refund", icon = "refund", color = 4, direction = Direction.IN, sortOrder = 5),
        Category(name = "Other income", icon = "more", color = 7, direction = Direction.IN, sortOrder = 6),
    )
}
