package com.pockit.app.data

// Purpose dictionary: label -> list of matching words (lower-cased).
// Keep one entry per label; add new labels by appending here.
val PURPOSE_DICTIONARY: List<Pair<String, List<String>>> = listOf(
    "Breakfast Time" to listOf("breakfast"),
    "Lunch Time" to listOf("lunch"),
    "Dinner Time" to listOf("dinner"),
    "Coffee Time" to listOf("coffee"),
    "Snacks" to listOf("snacks", "snack"),
    "Milk" to listOf("milk"),
    "Fruits" to listOf("fruits", "fruit"),
    "Vegetables" to listOf("vegetables", "vegetable", "veggies"),
    "Juice" to listOf("juice"),
    "Ice Cream" to listOf("ice cream", "icecream"),
    "Namkeen" to listOf("namkeen", "sev"),
    "Drinks" to listOf("drinks", "drink", "liquor", "mojito", "cold drinks", "cold drink",
        "soft drinks", "soft drink", "beer", "whisky", "vodka", "rum", "wine", "cocktail", "soda"),
    "Cafe" to listOf("cafe", "café"),
    "Food Order" to listOf("swiggy", "zomato", "food order"),
    "Groceries" to listOf("groceries", "grocery", "zepto", "blinkit", "instamart", "bigbasket"),
    "Shopping" to listOf("amazon", "myntra", "flipkart", "shopping"),
    "Rent" to listOf("rent"),
    "Deposit" to listOf("deposit", "security deposit"),
    "Electricity Bill" to listOf("electricity", "electricity bill", "light bill"),
    "WiFi Bill" to listOf("wifi", "wi-fi", "router", "wifi bill", "broadband"),
    "Recharge" to listOf("recharge", "mobile recharge", "wifi recharge", "air fiber", "airfiber",
        "jio air fiber", "jio airfiber", "jio fiber", "jiofiber", "jio"),
    "Credit Card Bill" to listOf("credit card", "credit card bill", "cc bill"),
    "Petrol" to listOf("petrol", "fuel", "diesel"),
    "Commute" to listOf("commute", "metro", "local train", "local"),
    "Auto Ride" to listOf("auto", "auto ride", "rickshaw", "rapido", "uber", "ola", "taxi", "cab"),
    "Tickets" to listOf("tickets", "ticket", "train tickets", "train ticket", "bus tickets",
        "flight tickets", "movie tickets", "movie", "entry tickets", "entry ticket", "entry"),
    "Perfume" to listOf("perfume", "deo", "deodorant"),
    "Clothes" to listOf("clothes", "clothing", "tshirt", "t-shirt", "tee", "shirt", "shoes", "shoe",
        "sneakers", "under garments", "undergarments", "innerwear", "jacket", "vest", "pants",
        "trousers", "shorts", "short", "jeans", "socks", "hoodie", "sweater"),
    "Medicines" to listOf("medicines", "medicine", "meds", "tablets", "pharmacy"),
    "Serum" to listOf("serum", "serums", "face serum", "hair serum"),
    "Shampoo" to listOf("shampoo"),
    "Conditioner" to listOf("conditioner"),
    "Minoxidil" to listOf("minoxidil"),
    "Moisturizer" to listOf("moisturizer", "moisturiser"),
    "Hair Styling" to listOf("hair clay", "clay", "hair wax", "wax", "mousse", "hair mousse"),
)

fun buildPurposeMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    for ((label, words) in PURPOSE_DICTIONARY) {
        for (word in words) map[word.lowercase()] = label
    }
    return map
}

val CITY_MAP: Map<String, String> = mapOf(
    "jdp" to "Jodhpur", "mum" to "Mumbai", "bom" to "Mumbai",
    "ajm" to "Ajmer", "del" to "Delhi", "jpr" to "Jaipur",
    "udr" to "Udaipur", "ahd" to "Ahmedabad", "pnq" to "Pune",
    "blr" to "Bengaluru", "hyd" to "Hyderabad", "maa" to "Chennai",
    "ccu" to "Kolkata", "lko" to "Lucknow", "idr" to "Indore",
    "bpl" to "Bhopal", "ngp" to "Nagpur", "kota" to "Kota",
    "jodhpur" to "Jodhpur", "mumbai" to "Mumbai", "ajmer" to "Ajmer",
    "delhi" to "Delhi", "jaipur" to "Jaipur", "udaipur" to "Udaipur",
    "ahmedabad" to "Ahmedabad", "pune" to "Pune", "bangalore" to "Bengaluru",
    "bengaluru" to "Bengaluru", "hyderabad" to "Hyderabad", "chennai" to "Chennai",
    "kolkata" to "Kolkata", "lucknow" to "Lucknow", "indore" to "Indore",
    "bhopal" to "Bhopal", "nagpur" to "Nagpur", "surat" to "Surat", "goa" to "Goa",
)
