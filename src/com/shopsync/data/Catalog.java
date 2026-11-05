package com.shopsync.data;

import com.shopsync.model.Category;
import com.shopsync.model.Product;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The master list of products ShopSync knows about.
 * Prices are in INR and roughly reflect typical Indian online prices.
 * In a real deployment this would come from the marketplaces themselves.
 */
public final class Catalog {

    private Catalog() { }

    public static List<Product> load() {
        return Collections.unmodifiableList(Arrays.asList(
            // ---------- Electronics ----------
            new Product("P01", "Apple iPhone 15 (128 GB, Black)", "Apple", Category.ELECTRONICS,
                    "📱", 69900, 79900, "phone", "smartphone", "mobile", "ios", "camera", "premium")
                .spec("Display", "6.1\" Super Retina XDR OLED")
                .spec("Chip", "A16 Bionic")
                .spec("Camera", "48 MP main + 12 MP ultra-wide")
                .spec("Battery", "Up to 20 h video playback")
                .spec("Charging", "USB-C, MagSafe")
                .alias("iphone", "iphone 15")
                .goodFor("iPhone users who want a dependable camera, years of software updates and resale value"),

            new Product("P02", "Samsung Galaxy S24 (256 GB)", "Samsung", Category.ELECTRONICS,
                    "📱", 74999, 89999, "phone", "smartphone", "mobile", "android", "camera", "premium")
                .spec("Display", "6.2\" Dynamic AMOLED 2X, 120 Hz")
                .spec("Chip", "Exynos 2400")
                .spec("Camera", "50 MP + 12 MP ultra-wide + 10 MP 3x telephoto")
                .spec("Battery", "4000 mAh")
                .spec("Software", "Galaxy AI, 7 years of OS updates")
                .alias("galaxy", "s24", "galaxy s24")
                .goodFor("Android fans who want a compact flagship with a zoom camera and AI features"),

            new Product("P03", "OnePlus Nord CE4 (8 GB / 128 GB)", "OnePlus", Category.ELECTRONICS,
                    "📱", 24999, 26999, "phone", "smartphone", "mobile", "android", "budget", "battery")
                .spec("Display", "6.7\" AMOLED, 120 Hz")
                .spec("Chip", "Snapdragon 7 Gen 3")
                .spec("Camera", "50 MP Sony LYT-600 main")
                .spec("Battery", "5500 mAh")
                .spec("Charging", "100 W SUPERVOOC")
                .alias("nord", "ce4", "oneplus nord")
                .goodFor("students and value seekers who want big battery life and very fast charging"),

            new Product("P04", "Sony WH-1000XM5 Wireless Headphones", "Sony", Category.ELECTRONICS,
                    "🎧", 26990, 34990, "audio", "headphones", "noise cancelling", "music", "travel", "premium")
                .spec("Noise cancelling", "Industry-leading ANC, 8 microphones")
                .spec("Battery", "Up to 30 h")
                .spec("Weight", "250 g")
                .spec("Codecs", "LDAC, AAC, SBC")
                .spec("Connectivity", "Bluetooth 5.2, multipoint")
                .alias("xm5", "wh-1000xm5", "1000xm5", "sony headphones")
                .goodFor("frequent travellers, commuters and anyone who wants silence and premium sound"),

            new Product("P05", "boAt Airdopes 141 TWS Earbuds", "boAt", Category.ELECTRONICS,
                    "🎧", 1199, 4490, "audio", "earbuds", "lifestyle", "budget", "music", "gift")
                .spec("Playback", "Up to 42 h with case")
                .spec("Latency", "BEAST mode, 50 ms low latency")
                .spec("Mic", "ENx environmental noise cancellation")
                .spec("Water resistance", "IPX4")
                .alias("airdopes", "boat earbuds")
                .goodFor("anyone on a tight budget who needs everyday earbuds for calls, music and gaming"),

            new Product("P06", "Apple AirPods Pro (2nd Gen)", "Apple", Category.ELECTRONICS,
                    "🎧", 22900, 26900, "audio", "earbuds", "noise cancelling", "ios", "music", "premium", "gift")
                .spec("Noise cancelling", "Adaptive ANC + Transparency")
                .spec("Chip", "Apple H2")
                .spec("Battery", "6 h (30 h with case)")
                .spec("Case", "USB-C MagSafe, IP54")
                .alias("airpods", "airpods pro")
                .goodFor("iPhone owners who want seamless pairing and strong noise cancellation in a pocketable size"),

            new Product("P07", "HP Pavilion 15 Laptop (i5, 16 GB, 512 GB SSD)", "HP", Category.ELECTRONICS,
                    "💻", 62990, 78000, "laptop", "computer", "student", "work")
                .spec("Processor", "Intel Core i5-1335U (13th Gen)")
                .spec("Memory", "16 GB DDR4")
                .spec("Storage", "512 GB NVMe SSD")
                .spec("Display", "15.6\" Full HD IPS")
                .spec("Weight", "1.75 kg")
                .alias("pavilion", "hp laptop")
                .goodFor("students and professionals who need a reliable all-rounder for coding, office work and streaming"),

            new Product("P08", "Samsung 43\" Crystal 4K Smart TV", "Samsung", Category.ELECTRONICS,
                    "📺", 31990, 52900, "tv", "television", "smart tv", "home", "entertainment")
                .spec("Resolution", "4K UHD (3840 × 2160)")
                .spec("Processor", "Crystal Processor 4K")
                .spec("HDR", "HDR10+")
                .spec("Audio", "20 W, Q-Symphony")
                .spec("Smart OS", "Tizen with Netflix, Prime Video, YouTube")
                .alias("samsung tv", "crystal 4k")
                .goodFor("small to medium living rooms and bedrooms that want 4K streaming without overspending"),

            new Product("P09", "Noise ColorFit Pro 5 Smartwatch", "Noise", Category.ELECTRONICS,
                    "⌚", 3999, 7999, "watch", "smartwatch", "wearable", "lifestyle", "fitness", "gift", "budget")
                .spec("Display", "1.85\" AMOLED, always-on")
                .spec("Calling", "Bluetooth calling")
                .spec("Battery", "Up to 7 days")
                .spec("Fitness", "100+ sports modes, SpO2, heart rate")
                .alias("colorfit", "noise watch")
                .goodFor("first-time smartwatch buyers who want fitness tracking and calling on a budget"),

            new Product("P10", "Logitech MX Master 3S Mouse", "Logitech", Category.ELECTRONICS,
                    "🖱️", 8995, 10995, "mouse", "computer", "accessory", "work", "productivity")
                .spec("Sensor", "8000 DPI, works on glass")
                .spec("Clicks", "Quiet clicks (90% less noise)")
                .spec("Scroll", "MagSpeed electromagnetic wheel")
                .spec("Battery", "Up to 70 days, USB-C")
                .spec("Multi-device", "Up to 3 devices")
                .alias("mx master", "master 3s", "logitech mouse")
                .goodFor("programmers, designers and anyone who spends long hours at a computer"),

            // ---------- Fashion ----------
            new Product("P11", "Nike Air Max SC Sneakers", "Nike", Category.FASHION,
                    "👟", 5995, 7495, "shoes", "sneakers", "footwear", "casual", "gift")
                .spec("Upper", "Leather and mesh")
                .spec("Cushioning", "Visible Max Air unit in the heel")
                .spec("Outsole", "Rubber, waffle pattern")
                .spec("Use", "Everyday / lifestyle")
                .alias("air max", "nike shoes", "nike sneakers")
                .goodFor("everyday casual wear with an iconic look and comfortable heel cushioning"),

            new Product("P12", "Adidas Runfalcon 3.0 Running Shoes", "Adidas", Category.FASHION,
                    "👟", 3599, 5999, "shoes", "running", "footwear", "sports", "fitness", "budget")
                .spec("Upper", "Breathable mesh")
                .spec("Midsole", "Cloudfoam cushioning")
                .spec("Outsole", "Rubber")
                .spec("Use", "Running, walking, gym")
                .alias("runfalcon", "adidas shoes")
                .goodFor("beginner runners and gym-goers who want light, cushioned shoes at a fair price"),

            new Product("P13", "Levi's 511 Slim Fit Jeans", "Levi's", Category.FASHION,
                    "👖", 2399, 3999, "jeans", "denim", "clothing", "casual")
                .spec("Fit", "Slim, sits below the waist")
                .spec("Fabric", "99% cotton, 1% elastane stretch denim")
                .spec("Rise", "Mid-rise")
                .spec("Care", "Machine wash cold")
                .alias("511", "levis", "levi's jeans")
                .goodFor("a versatile everyday pair that looks sharp without being skinny"),

            new Product("P14", "Puma Essentials Hoodie", "Puma", Category.FASHION,
                    "🧥", 1899, 3499, "hoodie", "sweatshirt", "clothing", "winter", "casual", "gift")
                .spec("Fabric", "Cotton-blend fleece")
                .spec("Fit", "Regular")
                .spec("Details", "Kangaroo pocket, drawcord hood")
                .alias("puma hoodie")
                .goodFor("chilly mornings, travel and layering in North Indian winters"),

            new Product("P15", "Ray-Ban Aviator Classic Sunglasses", "Ray-Ban", Category.FASHION,
                    "🕶️", 7490, 9290, "sunglasses", "eyewear", "accessory", "premium", "gift", "travel")
                .spec("Frame", "Metal, gold tone")
                .spec("Lens", "G-15 green glass")
                .spec("Protection", "100% UV")
                .spec("Size", "58 mm")
                .alias("aviator", "rayban", "ray ban")
                .goodFor("a timeless style piece and a classic premium gift"),

            // ---------- Home & Kitchen ----------
            new Product("P16", "Philips Air Fryer HD9252 (4.1 L)", "Philips", Category.HOME,
                    "🍟", 6999, 9995, "air fryer", "kitchen", "appliance", "cooking", "healthy", "gift")
                .spec("Capacity", "4.1 L (serves 2–3)")
                .spec("Power", "1400 W")
                .spec("Technology", "Rapid Air, up to 90% less fat")
                .spec("Controls", "Digital, 7 presets")
                .alias("air fryer", "airfryer", "philips fryer")
                .goodFor("small families and hostel-to-first-flat movers who want quick, low-oil cooking"),

            new Product("P17", "Prestige Svachh 5 L Pressure Cooker", "Prestige", Category.HOME,
                    "🍲", 1849, 2950, "cooker", "kitchen", "cookware", "cooking", "budget")
                .spec("Capacity", "5 L")
                .spec("Material", "Stainless steel")
                .spec("Compatibility", "Gas and induction")
                .spec("Lid", "Svachh spillage-control lid")
                .alias("pressure cooker", "prestige cooker", "svachh")
                .goodFor("everyday Indian cooking for a family of 3–5"),

            new Product("P18", "Milton Thermosteel Flask (1 L)", "Milton", Category.HOME,
                    "🧴", 899, 1350, "bottle", "flask", "kitchen", "travel", "budget", "gift")
                .spec("Capacity", "1 L")
                .spec("Insulation", "Hot or cold for 24 h")
                .spec("Material", "Stainless steel, vacuum insulated")
                .spec("Lid", "Leak-proof")
                .alias("milton", "thermosteel", "flask")
                .goodFor("office, college and travel; keeps tea hot or water cold all day")
        ));
    }
}
