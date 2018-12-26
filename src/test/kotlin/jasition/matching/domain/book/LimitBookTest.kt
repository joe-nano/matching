package jasition.matching.domain.book

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import jasition.matching.domain.EventId
import jasition.matching.domain.aBookEntry
import jasition.matching.domain.book.entry.EntryQuantity
import jasition.matching.domain.book.entry.EntryStatus
import jasition.matching.domain.book.entry.Price
import jasition.matching.domain.book.entry.Side
import java.time.Instant

internal class LimitBookTest : StringSpec({
    "Prioritises BUY entry of higher prices over lower prices"{
        val lowerPrice = aBookEntry(side = Side.BUY, price = Price(10))
        val higherPrice = aBookEntry(side = Side.BUY, price = Price(11))

        val newBook = LimitBook(Side.BUY).add(lowerPrice).add(higherPrice)

        newBook.entries.size() shouldBe 2
        newBook.entries.values().get(0) shouldBe higherPrice
        newBook.entries.values().get(1) shouldBe lowerPrice
    }
    "Prioritises BUY entry of earlier submission over later given the same price"{
        val now = Instant.now()
        val later = aBookEntry(side = Side.BUY, whenSubmitted = now)
        val earlier = aBookEntry(side = Side.BUY, whenSubmitted = now.minusMillis(1))

        val newBook = LimitBook(Side.BUY).add(later).add(earlier)

        newBook.entries.size() shouldBe 2
        newBook.entries.values().get(0) shouldBe earlier
        newBook.entries.values().get(1) shouldBe later
    }
    "Prioritises BUY entry of smaller Event ID over bigger given the same price and same submission time"{
        val now = Instant.now()
        val bigger = aBookEntry(side = Side.BUY, eventId = EventId(2), whenSubmitted = now)
        val smaller = aBookEntry(side = Side.BUY, eventId = EventId(1), whenSubmitted = now)

        val newBook = LimitBook(Side.BUY).add(bigger).add(smaller)

        newBook.entries.size() shouldBe 2
        newBook.entries.values().get(0) shouldBe smaller
        newBook.entries.values().get(1) shouldBe bigger
    }
    "Replaces BUY entry of same price, same submission time, and same Event ID"{
        val now = Instant.now()
        val entry1 = aBookEntry(side = Side.BUY, whenSubmitted = now, status = EntryStatus.NEW)
        val entry2 = aBookEntry(side = Side.BUY, whenSubmitted = now, status = EntryStatus.PARTIAL_FILL)

        val newBook = LimitBook(Side.BUY).add(entry1).add(entry2)

        newBook.entries.size() shouldBe 1
        newBook.entries.values().get(0) shouldBe entry2
    }
    "Prioritises SELL entry of lower prices over higher prices"{
        val lowerPrice = aBookEntry(side = Side.SELL, price = Price(10))
        val higherPrice = aBookEntry(side = Side.SELL, price = Price(11))

        val newBook = LimitBook(Side.SELL).add(lowerPrice).add(higherPrice)

        newBook.entries.size() shouldBe 2
        newBook.entries.values().get(0) shouldBe lowerPrice
        newBook.entries.values().get(1) shouldBe higherPrice
    }
    "Prioritises SELL entry of earlier submission over later given the same price"{
        val now = Instant.now()
        val later = aBookEntry(side = Side.SELL, whenSubmitted = now)
        val earlier = aBookEntry(side = Side.SELL, whenSubmitted = now.minusMillis(1))

        val newBook = LimitBook(Side.SELL).add(later).add(earlier)

        newBook.entries.size() shouldBe 2
        newBook.entries.values().get(0) shouldBe earlier
        newBook.entries.values().get(1) shouldBe later
    }
    "Prioritises SELL entry of smaller Event ID over bigger given the same price and same submission time"{
        val now = Instant.now()
        val bigger = aBookEntry(side = Side.SELL, eventId = EventId(2), whenSubmitted = now)
        val smaller = aBookEntry(side = Side.SELL, eventId = EventId(1), whenSubmitted = now)

        val newBook = LimitBook(Side.SELL).add(bigger).add(smaller)

        newBook.entries.size() shouldBe 2
        newBook.entries.values().get(0) shouldBe smaller
        newBook.entries.values().get(1) shouldBe bigger
    }
    "Replaces SELL entry of same price, same submission time, and same Event ID"{
        val now = Instant.now()
        val entry1 = aBookEntry(side = Side.SELL, whenSubmitted = now, status = EntryStatus.NEW)
        val entry2 = aBookEntry(side = Side.SELL, whenSubmitted = now, status = EntryStatus.PARTIAL_FILL)

        val newBook = LimitBook(Side.SELL).add(entry1).add(entry2)

        newBook.entries.size() shouldBe 1
        newBook.entries.values().get(0) shouldBe entry2
    }
    "Updates partial fill entry on the book"{
        val original = aBookEntry(side = Side.SELL,
            price = Price(10),
            size = EntryQuantity(availableSize = 10, tradedSize = 5, cancelledSize = 0)
        )
        val updated = original.toTradeSideEntry(7)
        val newBook = LimitBook(Side.SELL).add(original).update(updated)

        newBook.entries.size() shouldBe 1
        newBook.entries.values().get(0) shouldBe original.copy(
            size = EntryQuantity(availableSize = 3, tradedSize = 12, cancelledSize = 0),
            status = EntryStatus.PARTIAL_FILL
        )
    }
    "Removes filled entry from the book"{
        val original = aBookEntry(side = Side.SELL,
            price = Price(10),
            size = EntryQuantity(availableSize = 10, tradedSize = 5, cancelledSize = 0)
        )
        val updated = original.toTradeSideEntry(10)
        val newBook = LimitBook(Side.SELL).add(original).update(updated)

        newBook.entries.size() shouldBe 0
    }
})