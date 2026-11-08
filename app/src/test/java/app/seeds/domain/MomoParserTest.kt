package app.seeds.domain

import app.seeds.data.db.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MomoParserTest {

    @Test fun received() {
        val p = MomoParser.parse(
            "You have received 25,000 RWF from JOHN DOE (*********123) on your mobile money account at 2026-11-02 10:21:33. " +
                "Message from sender: rent. Your new balance:41,500 RWF. Financial Transaction Id: 1234567890.",
        )!!
        assertEquals(25_000, p.amount)
        assertEquals(Direction.IN, p.direction)
        assertEquals(41_500L, p.balanceAfter)
        assertEquals("John Doe", p.counterparty)
        assertEquals("1234567890", p.txId)
    }

    @Test fun payment() {
        val p = MomoParser.parse(
            "TxId: 99887766. Your payment of 3,500 RWF to SIMBA SUPERMARKET 12345 was completed at 2026-11-02 18:01:10. " +
                "Your new balance: 38,000 RWF. Fee was 0 RWF.",
        )!!
        assertEquals(3_500, p.amount)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(38_000L, p.balanceAfter)
        assertEquals(0L, p.fee)
        assertEquals("99887766", p.txId)
    }

    @Test fun transfer() {
        val p = MomoParser.parse(
            "*165*S*10000 RWF transferred to JANE UWASE (250788123456) from 4512 at 2026-11-03 09:00:00 . Fee was: 100 RWF. New balance: 27900 RWF.",
        )!!
        assertEquals(10_000, p.amount)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(100L, p.fee)
        assertEquals(27_900L, p.balanceAfter)
        assertEquals("Jane Uwase", p.counterparty)
    }

    @Test fun withdrawal() {
        val p = MomoParser.parse(
            "You have via agent: AGENT SHOP (250788000000), withdrawn 20000 RWF from your mobile money account: 4512 at 2026-11-04 12:00:00. " +
                "Your new balance: 7,560 RWF. Fee paid: 340 RWF.",
        )!!
        assertEquals(20_000, p.amount)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(340L, p.fee)
        assertEquals(7_560L, p.balanceAfter)
    }

    @Test fun ignoresFailedAndOtp() {
        assertNull(MomoParser.parse("Transaction failed. Insufficient balance to pay 5,000 RWF."))
        assertNull(MomoParser.parse("Your one-time code is 448812"))
        assertNotNull(MomoParser.parse("Your payment of 1,200 RWF for shopping to KIOSK was completed. New balance: 500 RWF."))
    }
}
