package com.fit100.tracker
fun main() {
 val recipes=DietFood.recipes
 check(DietFood.defaults.size==DietFood.slots.size)
 check(DietFood.defaults.all { it in recipes.indices })
 check(recipes.all { it.kcal>0 && it.protein>=0 && it.carbs>=0 && it.fat>=0 && it.steps.isNotEmpty() && it.ingredients.isNotEmpty() })
 val vegan=recipes.filter { DietFood.allowed(it,"Vegan","","Dairy-free") }
 check(vegan.isNotEmpty());check(vegan.none { it.tags.any { tag->tag in setOf("Eggs","Chicken","Milk") } })
 check(!DietFood.allowed(recipes[0],"Vegetarian","","Include"))
 check(DietFood.allowed(recipes[0],"Eggetarian","","Dairy-free"))
 check(!DietFood.allowed(recipes[2],"Eggetarian","","Include"))
 check(!DietFood.allowed(recipes[0],"Non-vegetarian"," EGGS,  onion ","Include"))
 check(!DietFood.allowed(recipes[3],"Vegan","peanut","Include"))
 check(DietFood.allowed(recipes[5],"Vegan","eggs, peanuts","Dairy-free"))
 println("Recipe coverage and preference/exclusion checks passed")
}
