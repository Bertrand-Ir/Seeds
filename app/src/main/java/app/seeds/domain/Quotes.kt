package app.seeds.domain

import java.time.LocalDate

data class Quote(val text: String, val author: String)

/**
 * Words about time — the one thing nobody gets back. Picked by the day, then rotated each time
 * you drift again, so the same moment rarely repeats.
 */
object Quotes {
    val all = listOf(
        Quote("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"),
        Quote("While we are postponing, life speeds by.", "Seneca"),
        Quote("Life is long if you know how to use it.", "Seneca"),
        Quote("Hold every hour in your grasp. Lay hold of today's task, and you will not need to depend so much upon tomorrow's.", "Seneca"),
        Quote("Nothing is ours, except time.", "Seneca"),
        Quote("You could leave life right now. Let that determine what you do and say and think.", "Marcus Aurelius"),
        Quote("Do every act of your life as though it were the very last act of your life.", "Marcus Aurelius"),
        Quote("Very little is needed to make a happy life; it is all within yourself, in your way of thinking.", "Marcus Aurelius"),
        Quote("Confine yourself to the present.", "Marcus Aurelius"),
        Quote("Teach us to number our days, that we may gain a heart of wisdom.", "Psalm 90:12"),
        Quote("Make the best use of the time, because the days are evil.", "Ephesians 5:16"),
        Quote("Whatever your hand finds to do, do it with all your might.", "Ecclesiastes 9:10"),
        Quote("There is a time for everything, and a season for every activity under the heavens.", "Ecclesiastes 3:1"),
        Quote("Lost time is never found again.", "Benjamin Franklin"),
        Quote("Dost thou love life? Then do not squander time, for that is the stuff life is made of.", "Benjamin Franklin"),
        Quote("You may delay, but time will not.", "Benjamin Franklin"),
        Quote("A man who dares to waste one hour of time has not discovered the value of life.", "Charles Darwin"),
        Quote("As if you could kill time without injuring eternity.", "Henry David Thoreau"),
        Quote("It's not enough to be busy; so are the ants. The question is: what are we busy about?", "Henry David Thoreau"),
        Quote("Time is the coin of your life. Only you can determine how it will be spent.", "Carl Sandburg"),
        Quote("The journey of a thousand miles begins with one step.", "Lao Tzu"),
        Quote("Well begun is half done.", "Aristotle"),
        Quote("We are what we repeatedly do.", "Will Durant, on Aristotle"),
        Quote("Seize the day, trusting as little as possible in tomorrow.", "Horace"),
        Quote("Time flies over us, but leaves its shadow behind.", "Nathaniel Hawthorne"),
        Quote("Time is what we want most, but what we use worst.", "William Penn"),
        Quote("The bad news is time flies. The good news is you're the pilot.", "Michael Altshuler"),
        Quote("One today is worth two tomorrows.", "Benjamin Franklin"),
        Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"),
        Quote("Begin at once to live, and count each separate day as a separate life.", "Seneca"),
        Quote("How we spend our days is, of course, how we spend our lives.", "Annie Dillard"),
        Quote("The key is in not spending time, but in investing it.", "Stephen R. Covey"),
        Quote("Discipline is choosing between what you want now and what you want most.", "Augusta F. Kantra"),
        Quote("An inch of time is an inch of gold, but you can't buy that inch of time with an inch of gold.", "Chinese proverb"),
        Quote("The best time to plant a tree was twenty years ago. The second best time is now.", "Proverb"),
        Quote("Little by little, one travels far.", "J.R.R. Tolkien"),
        Quote("Do not squander time. That is the stuff life is made of.", "Benjamin Franklin"),
        Quote("Your future is created by what you do today, not tomorrow.", "Robert Kiyosaki"),
        // In the Seeds voice
        Quote("The feed will still be there tonight. This hour won't.", "Seeds"),
        Quote("Every minute here is a minute not given to what you said matters.", "Seeds"),
        Quote("You don't need more time. You need this time back.", "Seeds"),
        Quote("Scrolling is easy. Becoming who you want to be isn't — and that's why it's worth it.", "Seeds"),
        Quote("Small seeds, every day. Not every day, someday.", "Seeds"),
        Quote("Nobody on their last day wished they'd scrolled a little longer.", "Seeds"),
        Quote("You planted something this morning. Go water it.", "Seeds"),
        Quote("Life is too short to spend it watching other people live theirs.", "Seeds"),
        Quote("The app was designed to keep you. You were designed for more.", "Seeds"),
        Quote("Close it now and you'll be proud in an hour.", "Seeds"),
    )

    /** Quote of the day, nudged forward each time you drift again today. */
    fun pick(date: LocalDate, nth: Int): Quote {
        val base = (date.year * 372 + date.dayOfYear * 31) % all.size
        return all[(base + nth * 7) % all.size]
    }
}
