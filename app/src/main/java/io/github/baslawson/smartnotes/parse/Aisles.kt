package io.github.baslawson.smartnotes.parse

/** The shop's aisles, in the order "By aisle" shows them (Other last). */
enum class Aisle(val label: String) {
    FRUIT_VEG("Fruit & veg"),
    BAKERY("Bakery"),
    MEAT_FISH("Meat & fish"),
    DAIRY("Dairy & eggs"),
    FROZEN("Frozen"),
    PANTRY("Pantry"),
    BREAKFAST("Breakfast"),
    SNACKS("Snacks & sweets"),
    DRINKS("Drinks"),
    HOUSEHOLD("Household"),
    CARE("Health & beauty"),
    BABY_PETS("Baby & pets"),
    OTHER("Other");

    companion object {
        /** The aisle stored under [name] (an [Aisle] name); null for anything else. */
        fun of(name: String?): Aisle? = entries.firstOrNull { it.name == name }
    }
}

/**
 * A starter word list that puts an item in an aisle by its name ("2x oat milk" → Dairy & eggs), English words only.
 * Two-word names count before single words ("peanut butter" is Pantry, not Dairy), and the last word before the
 * others ("chocolate milk" is milk). An item the list doesn't know is [Aisle.OTHER]; "Aisle…" on an item overrides it.
 */
object Aisles {
    private val PHRASES: Map<String, Aisle> = mapOf(
        "peanut butter" to Aisle.PANTRY, "ice cream" to Aisle.FROZEN, "ice lolly" to Aisle.FROZEN,
        "fish finger" to Aisle.FROZEN, "frozen pea" to Aisle.FROZEN, "frozen pizza" to Aisle.FROZEN,
        "coconut milk" to Aisle.PANTRY, "tomato puree" to Aisle.PANTRY, "tomato paste" to Aisle.PANTRY,
        "olive oil" to Aisle.PANTRY, "soy sauce" to Aisle.PANTRY, "stock cube" to Aisle.PANTRY,
        "baked bean" to Aisle.PANTRY, "spring onion" to Aisle.FRUIT_VEG, "sweet potato" to Aisle.FRUIT_VEG,
        "toilet paper" to Aisle.HOUSEHOLD, "toilet roll" to Aisle.HOUSEHOLD, "kitchen roll" to Aisle.HOUSEHOLD,
        "washing up liquid" to Aisle.HOUSEHOLD, "bin bag" to Aisle.HOUSEHOLD, "cling film" to Aisle.HOUSEHOLD,
        "dish soap" to Aisle.HOUSEHOLD, "hand soap" to Aisle.CARE, "cat food" to Aisle.BABY_PETS,
        "dog food" to Aisle.BABY_PETS, "cat litter" to Aisle.BABY_PETS, "baby food" to Aisle.BABY_PETS,
        "orange juice" to Aisle.DRINKS, "apple juice" to Aisle.DRINKS, "sparkling water" to Aisle.DRINKS,
        "corn flake" to Aisle.BREAKFAST, "cream cheese" to Aisle.DAIRY, "sour cream" to Aisle.DAIRY,
        "hot chocolate" to Aisle.BREAKFAST, "chocolate spread" to Aisle.BREAKFAST,
        "sun cream" to Aisle.CARE, "hand cream" to Aisle.CARE, "face cream" to Aisle.CARE, "shaving cream" to Aisle.CARE,
        "butternut squash" to Aisle.FRUIT_VEG, "orange squash" to Aisle.DRINKS, "lemon squash" to Aisle.DRINKS,
        "fruit squash" to Aisle.DRINKS,
    )

    private val WORDS: Map<String, Aisle> = buildMap {
        fun put(aisle: Aisle, words: String) = words.split(' ').forEach { put(it, aisle) }
        put(Aisle.FRUIT_VEG, "apple banana orange lemon lime pear grape strawberry raspberry blueberry berry melon " +
            "mango pineapple peach plum kiwi avocado tomato potato onion garlic carrot pepper cucumber lettuce salad " +
            "spinach broccoli cauliflower cabbage courgette zucchini aubergine eggplant mushroom celery leek pea bean " +
            "corn ginger herb basil veggie parsley coriander cilantro mint kale beetroot radish squash pumpkin fruit veg vegetable")
        put(Aisle.BAKERY, "bread loaf roll bagel baguette croissant bun muffin wrap tortilla pitta pita naan crumpet " +
            "cake brioche sourdough")
        put(Aisle.MEAT_FISH, "chicken beef pork lamb mince steak sausage bacon ham turkey salami chorizo meat fish " +
            "salmon tuna cod prawn shrimp mackerel")
        put(Aisle.DAIRY, "milk cheese butter yogurt yoghurt cream egg margarine cheddar mozzarella parmesan feta " +
            "halloumi custard")
        put(Aisle.FROZEN, "frozen ice")
        put(Aisle.PANTRY, "pasta spaghetti rice noodle flour sugar salt oil vinegar sauce ketchup mayo mayonnaise " +
            "mustard stock spice lentil chickpea tin can soup honey jam baking yeast couscous quinoa")
        put(Aisle.BREAKFAST, "cereal oat oats porridge muesli granola cornflake coffee tea marmalade")
        put(Aisle.SNACKS, "crisp chip chocolate biscuit cookie sweet candy nut popcorn cracker snack")
        put(Aisle.DRINKS, "water juice beer wine cola lemonade soda smoothie cider gin vodka whisky")
        put(Aisle.HOUSEHOLD, "detergent bleach sponge foil battery bulb tissue napkin cleaner softener dishwasher " +
            "laundry")
        put(Aisle.CARE, "shampoo conditioner soap toothpaste toothbrush deodorant razor plaster paracetamol " +
            "ibuprofen vitamin lotion sunscreen")
        put(Aisle.BABY_PETS, "nappy nappies diaper wipe formula")
    }

    /** An item's aisle: the one chosen for its [key] in [chosen], else the word list's guess from its [name]. */
    fun of(key: String, name: String, chosen: Map<String, Aisle>): Aisle = chosen[key] ?: guess(name)

    /** The aisle [name] belongs in by the word list. */
    fun guess(name: String): Aisle {
        val words = Shopping.key(Shopping.parse(name)?.name ?: name).split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.map(::single)
        if (words.isEmpty()) return Aisle.OTHER
        // "frozen spinach", "frozen sweet potatoes": the freezer, whatever it is.
        if ("frozen" in words) return Aisle.FROZEN
        // Longest names first (three words, then two), from the end of the name.
        for (size in minOf(3, words.size) downTo 2) {
            for (start in words.size - size downTo 0) {
                PHRASES[words.subList(start, start + size).joinToString(" ")]?.let { return it }
            }
        }
        for (w in words.asReversed()) WORDS[w]?.let { return it }
        return Aisle.OTHER
    }

    /** One of a plural ("tomatoes" → tomato, "berries" → berry), as the word list has them. */
    private fun single(word: String): String = when {
        word in WORDS || word in KEEP -> word
        // "cookies" → cookie (before "berries" → berry).
        word.endsWith("ies") && word.dropLast(1) in WORDS -> word.dropLast(1)
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("oes") || word.endsWith("ches") || word.endsWith("shes") -> word.dropLast(2)
        word.endsWith("s") && !word.endsWith("ss") && word.length > 3 -> word.dropLast(1)
        else -> word
    }

    private val KEEP = setOf("oats", "nappies", "hummus", "couscous", "asparagus")
}
