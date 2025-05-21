package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
class RecipesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecipeAdapter

    private val recipes = listOf(
        // Lattes
        RecipeRow.SectionHeader("Lattes"),
        RecipeRow.RecipeItem(Recipe("Chai Latte", listOf("12 oz: 6 oz chai + 6 oz milk", "16 oz: 8 oz chai + 8 oz milk", "20 oz: 10 oz chai + 10 oz milk"))),
        RecipeRow.RecipeItem(Recipe("Cappuccino", listOf("Foam: 1/3 cup, not glossy", "Small: 2 shots", "Medium: 2 shots", "Large: 3 shots", "X Large: 3 shots"))),
        RecipeRow.RecipeItem(Recipe("Coconut Creme Latte", listOf("12 oz: 1.5 oz coconut + 1 vanilla pump", "16 oz: 2 oz coconut + 1 vanilla pump", "20 oz: 2.5 oz coconut + 1 vanilla pump"))),
        RecipeRow.RecipeItem(Recipe("Flat White", listOf("8 oz only", "Glossy, micro foam"))),
        RecipeRow.RecipeItem(Recipe("Latte", listOf("Steam/froth milk after adding syrups", "¼ cup foam, glossy paint texture"))),
        RecipeRow.RecipeItem(Recipe("Latte Macchiato", listOf("Pour milk first, then shots/syrups"))),
        RecipeRow.RecipeItem(Recipe("Matcha Latte", listOf("Iced: mix matcha + water", "Hot: mix matcha with milk", "12 oz: 1 tsp matcha", "16 oz: 2 tsp", "20 oz: 3 tsp"))),
        RecipeRow.RecipeItem(Recipe("Vanilla Latte", listOf("1 shot espresso", "1 tbsp vanilla syrup", "1 cup milk", "Steam and mix"))),

        // Macchiatos
        RecipeRow.SectionHeader("Macchiatos"),
        RecipeRow.RecipeItem(Recipe("Caramel Macchiato", listOf("Milk first, then shots/syrup/sauce", "12 oz: 1 caramel + 1 vanilla", "16 oz: 1½ caramel + 1 vanilla", "20 oz: 2 caramel + 1 vanilla"))),
        RecipeRow.RecipeItem(Recipe("Macchiato", listOf("8 oz only", "1 espresso shot", "Scoop of foam"))),

        // Mochas / Chocolate
        RecipeRow.SectionHeader("Mochas & Chocolate"),
        RecipeRow.RecipeItem(Recipe("Abuelitas Chocolate/Mocha", listOf("Iced: blend with milk, no foam", "Hot: steam milk + powder", "12 oz: 2½ tbsp", "16 oz: 3 tbsp", "20 oz: 3½ tbsp"))),
        RecipeRow.RecipeItem(Recipe("Hot Chocolate", listOf("Kids temp: 120°–130°", "8 oz: 1½ pumps", "12 oz: 2 pumps", "16 oz: 2½ pumps", "20 oz: 3 pumps"))),

        // Teas & Lemonades
        RecipeRow.SectionHeader("Teas & Lemonades"),
        RecipeRow.RecipeItem(Recipe("Fruit Puree Teas", listOf("12 oz: 4 oz tea, 3 oz lemonade, 1 puree, 1 simple", "16 oz: 5 oz tea, 4 oz lemonade, 1.5 puree, 1 simple", "20 oz: 6 oz tea, 5 oz lemonade, 2 puree, 1 simple"))),
        RecipeRow.RecipeItem(Recipe("Lemonade Teas", listOf("12 oz: 4 oz tea, 3 oz lemonade, 2 syrup", "16 oz: 5 oz tea, 4 oz lemonade, 2.5 syrup", "20 oz: 6 oz tea, 5 oz lemonade, 3 syrup"))),
        RecipeRow.RecipeItem(Recipe("Matcha my Heart", listOf("Matcha, oatmilk, strawberry foam", "12 oz: 1 matcha, 1 strawberry, 2 oz cream", "16 oz: 2 matcha, 1 strawberry, 2 oz cream", "20 oz: 3 matcha, 1½ strawberry, 3 oz cream"))),
        RecipeRow.RecipeItem(Recipe("Rose Green Tea", listOf("Green tea", "Rose syrup", "Edible glitter"))),
        RecipeRow.RecipeItem(Recipe("Lovebug Lemonade / Strawberry Splash", listOf("Strawberry-cranberry + lemonade", "12 oz: Juice to ‘B’, lemonade to ‘C’, 1 scoop berries", "16 oz: Juice to ‘C’, lemonade to ‘D’", "20 oz: Juice to ‘D’, lemonade to ‘E’"))),

        // Blended Drinks & Smoothies
        RecipeRow.SectionHeader("Blended Drinks & Smoothies"),
        RecipeRow.RecipeItem(Recipe("Blended Drinks", listOf("Blend all + full ice", "12 oz: 2 shots + 1 pump + splash", "16 oz: 2 shots + 1½ pumps + splash", "20 oz: 3 shots + 2 pumps + splash"))),
        RecipeRow.RecipeItem(Recipe("Smoothies (In General)", listOf("16 oz only", "8 oz smoothie mix", "Extra full cup ice"))),
        RecipeRow.RecipeItem(Recipe("Tropical Sunrise Smoothie", listOf("1/3 Mango", "1/3 Orange", "1/3 Pina Colada", "Extra full cup ice"))),
        RecipeRow.RecipeItem(Recipe("Berry Island Smoothie", listOf("1/3 Mixed Berry", "1/3 Strawberry", "1/3 Pina Colada", "Extra full cup ice"))),
        RecipeRow.RecipeItem(Recipe("Peach Paradise Smoothie", listOf("1/3 Peach", "1/3 Lemonade", "1/3 Cherry", "Extra full cup ice"))),
        RecipeRow.RecipeItem(Recipe("Mango Tango Smoothie", listOf("1/3 Mango", "1/3 Orange", "1/3 Strawberry Banana", "Extra full cup ice"))),

        // Coffee & Cold Brew
        RecipeRow.SectionHeader("Coffee & Cold Brew"),
        RecipeRow.RecipeItem(Recipe("Americano", listOf("Espresso on top of water", "12 oz: 2 shots", "16 oz: 3 shots", "20 oz: 4 shots"))),
        RecipeRow.RecipeItem(Recipe("Cold Brew Concentrate", listOf("12 oz: 4 oz cold brew + 3 oz water", "16 oz: 5 oz cold brew + 4 oz water", "20 oz: 6 oz cold brew + 5 oz water"))),
        RecipeRow.RecipeItem(Recipe("Vietnamese Coffee", listOf("Full ice", "12 oz: 2 oz condensed milk + 3 shots", "16 oz: 3 oz condensed milk + 4 shots", "20 oz: 4 oz condensed milk + 5 shots"))),

        // Seasonal Drinks
        RecipeRow.SectionHeader("Seasonal Drinks"),
        RecipeRow.RecipeItem(Recipe("Pumpkin Chai", listOf("12 oz: 1½ chai + 1 pumpkin", "16 oz: 2 chai + 1½ pumpkin", "20 oz: 3 chai + 2 pumpkin", "Top cinnamon + pumpkin spice"))),
        RecipeRow.RecipeItem(Recipe("Smores Mocha", listOf("Toasted marshmallow + chocolate sauce", "Top: graham cracker powder"))),
        RecipeRow.RecipeItem(Recipe("Raspberry Mocha", listOf("Raspberry syrup + chocolate sauce"))),
        RecipeRow.RecipeItem(Recipe("Peppermint Mocha", listOf("Peppermint syrup + chocolate sauce"))),
        RecipeRow.RecipeItem(Recipe("Chocolate Rose Latte", listOf("12 oz: 1 chocolate + 1½ rose", "16 oz: 1 chocolate + 2 rose", "20 oz: 1 chocolate + 2½ rose"))),
        RecipeRow.RecipeItem(Recipe("Honey Pistachio Latte", listOf("12 oz: 1 Honey + 2 Pistachio", "16 oz: 1½  Honey + 2 Pistachio", "20 oz: 2 Honey + 2 Pistachio"))),
        RecipeRow.RecipeItem(Recipe("Hot Chocolate w/ Marshmallows", listOf("Whipped cream", "Marshmallows", "Chocolate powder", "Crushed peppermint", "No lid")))
    )
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_recipes, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewRecipes)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = RecipeAdapter(recipes)
        recyclerView.adapter = adapter
        return view
    }
}

data class Recipe(val name: String, val steps: List<String>)