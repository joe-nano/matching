package jasition.matching.domain.trade

import io.vavr.collection.List
import io.vavr.collection.Seq
import jasition.cqrs.Event
import jasition.cqrs.EventId
import jasition.cqrs.Transaction
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.LimitBook
import jasition.matching.domain.book.entry.BookEntry
import jasition.matching.domain.book.entry.Price
import jasition.matching.domain.client.Client
import jasition.matching.domain.trade.event.TradeEvent

fun matchAndFinalise(
    aggressor: BookEntry,
    books: Books
): Transaction<BookId, Books> {
    val result = match(
        aggressor = aggressor,
        books = books
    )

    return result.aggressor.timeInForce.finalise(aggressor, books, result)
}

fun match(
    aggressor: BookEntry,
    books: Books,
    lastEventId: EventId = books.lastEventId,
    events: List<Event<BookId, Books>> = List.empty()
): MatchingResult {
    val limitBook = aggressor.side.oppositeSideBook(books)

    if (cannotMatchAnyFurther(aggressor, limitBook)) {
        return MatchingResult(aggressor, Transaction(books, events))
    }

    val nextMatch = findNextMatch(aggressor, limitBook.entries.values())
        ?: return MatchingResult(aggressor, Transaction(books, events))

    val eventId = lastEventId.inc()
    val tradeSize = getTradeSize(aggressor.sizes, nextMatch.passive.sizes)
    val tradedAggressor = aggressor.traded(tradeSize)
    val tradedPassive = nextMatch.passive.traded(tradeSize)
    val tradeEvent = TradeEvent(
        bookId = books.bookId,
        eventId = eventId,
        size = tradeSize,
        price = nextMatch.tradePrice,
        whenHappened = aggressor.key.whenSubmitted,
        aggressor = tradedAggressor.toTradeSideEntry(),
        passive = tradedPassive.toTradeSideEntry()
    )
    val result = tradeEvent.play(books)

    return match(
        aggressor = tradedAggressor,
        books = result,
        lastEventId = eventId,
        events = events.append(tradeEvent)
    )
}

fun findNextMatch(
    aggressor: BookEntry,
    passives: Seq<BookEntry>,
    offset: Int = 0
): Match? {
    return findPassive(passives, offset)?.let { passive ->
        if (cannotMatchTheseTwoPrices(aggressor.key.price, passive.key.price)
            || cannotMatchTheseTwoClients(aggressor.whoRequested, passive.whoRequested)
            || cannotMatchTheseTwoEntries(aggressor.isQuote, passive.isQuote)
        ) return findNextMatch(
            aggressor = aggressor,
            passives = passives,
            offset = offset + 1
        )

        findTradePrice(
            aggressorSide = aggressor.side,
            aggressor = aggressor.key.price,
            passive = passive.key.price
        )?.let { tradePrice ->
            return Match(passive, tradePrice)
        }
    }
}

private fun cannotMatchAnyFurther(aggressor: BookEntry, limitBook: LimitBook): Boolean =
    aggressor.sizes.available <= 0 || limitBook.entries.isEmpty

private fun cannotMatchTheseTwoClients(aggressor: Client, passive: Client): Boolean =
    sameFirmAndSameFirmClient(aggressor, passive) || sameFirmButPossibleFirmAgainstClient(aggressor, passive)

private fun cannotMatchTheseTwoEntries(aggressorIsQuote: Boolean, passiveIsQuote: Boolean): Boolean =
    aggressorIsQuote && passiveIsQuote

private fun cannotMatchTheseTwoPrices(aggressor: Price?, passive: Price?): Boolean =
    aggressor == null && passive == null

private fun findPassive(passives: Seq<BookEntry>, offset: Int): BookEntry? =
    if (offset < passives.size()) passives.get(offset) else null

data class Match(val passive: BookEntry, val tradePrice: Price)

data class MatchingResult(val aggressor: BookEntry, val transaction: Transaction<BookId, Books>)
