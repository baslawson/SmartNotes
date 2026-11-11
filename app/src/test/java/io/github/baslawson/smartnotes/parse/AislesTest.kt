package io.github.baslawson.smartnotes.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class AislesTest {
    @Test fun guessesTheAisleFromTheName() {
        assertEquals(Aisle.DAIRY, Aisles.guess("Milk"))
        assertEquals(Aisle.DAIRY, Aisles.guess("2x oat milk")) // the quantity is ignored, the last word counts
        assertEquals(Aisle.FRUIT_VEG, Aisles.guess("tomatoes")) // plurals
        assertEquals(Aisle.FRUIT_VEG, Aisles.guess("Blueberries"))
        assertEquals(Aisle.BAKERY, Aisles.guess("sourdough loaf"))
        assertEquals(Aisle.HOUSEHOLD, Aisles.guess("AA batteries"))
        assertEquals(Aisle.BREAKFAST, Aisles.guess("oats"))
    }

    @Test fun twoWordNamesComeBeforeSingleWords() {
        assertEquals(Aisle.PANTRY, Aisles.guess("peanut butter"))
        assertEquals(Aisle.FROZEN, Aisles.guess("vanilla ice cream"))
        assertEquals(Aisle.HOUSEHOLD, Aisles.guess("toilet paper"))
        assertEquals(Aisle.HOUSEHOLD, Aisles.guess("washing up liquid"))
        assertEquals(Aisle.BABY_PETS, Aisles.guess("cat food"))
    }

    @Test fun wordsTheReviewFoundMisplaced() {
        assertEquals(Aisle.SNACKS, Aisles.guess("cookies")) // cookie, not "cooky"
        assertEquals(Aisle.DRINKS, Aisles.guess("smoothies"))
        assertEquals(Aisle.FRUIT_VEG, Aisles.guess("veggies"))
        assertEquals(Aisle.FROZEN, Aisles.guess("frozen spinach")) // the freezer, whatever it is
        assertEquals(Aisle.FROZEN, Aisles.guess("Frozen prawns"))
        assertEquals(Aisle.FROZEN, Aisles.guess("frozen sweet potatoes")) // before the phrases too
        assertEquals(Aisle.CARE, Aisles.guess("sun cream"))
        assertEquals(Aisle.DAIRY, Aisles.guess("double cream"))
        assertEquals(Aisle.FRUIT_VEG, Aisles.guess("butternut squash"))
        assertEquals(Aisle.DRINKS, Aisles.guess("orange squash"))
    }

    @Test fun unknownNamesAreOther() {
        assertEquals(Aisle.OTHER, Aisles.guess("birthday card"))
        assertEquals(Aisle.OTHER, Aisles.guess("   "))
    }

    @Test fun aChosenAisleWins() {
        assertEquals(Aisle.SNACKS, Aisles.of("milk", "Milk", mapOf("milk" to Aisle.SNACKS)))
        assertEquals(Aisle.DAIRY, Aisles.of("milk", "Milk", mapOf("tea" to Aisle.SNACKS)))
        assertEquals(Aisle.DAIRY, Aisle.of("DAIRY"))
        assertEquals(null, Aisle.of("Gone"))
    }
}
