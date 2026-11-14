# Generates callsign word lists (exactly 256 each). Run: python -I gen_wordlists.py
import itertools, sys
ADJ = """Amber Ancient Arcane Ashen Astral Azure Blazing Bold Brass Brave Bright Bronze Burning Calm Cobalt Copper Coral
Cosmic Crimson Crystal Cursed Dark Dawn Deep Diamond Dire Distant Dusk Dusty Eager Ebony Electric Elder Ember Emerald Ender
Eternal Fading Feral Fierce Fiery Flint Floral Flying Foggy Frost Frozen Gentle Ghostly Gilded Glacial Gloomy Glowing Golden
Granite Grand Gray Green Grim Hidden Hollow Humble Icy Indigo Infinite Iron Ivory Jade Jolly Keen Lava Lazy Lone Lucky Lunar
Magma Marble Mighty Misty Molten Mossy Mystic Nether Neutron Nimble Noble Obsidian Ochre Olive Onyx Opal Orange Pale Pearl
Plasma Polar Primal Proud Purple Quick Quiet Radiant Rapid Red Regal Restless Ruby Rusty Sacred Sandy Sapphire Scarlet Shadow
Sharp Shining Silent Silver Sleepy Sly Smoky Solar Sonic Spectral Spiral Starry Steel Stellar Stone Stormy Sunny Swift Tawny
Thunder Tidal Tiny Topaz Toxic Turbo Twilight Umber Valiant Velvet Vibrant Violet Void Wandering Warm Wild Windy Winter Wise
Witty Woven Zealous Zinc Agile Alpine Atomic Autumn Bitter Blessed Boreal Brisk Bubbly Chrome Clever Cloudy Crafty Crooked
Curious Daring Dazzling Dense Dizzy Dreamy Echoing Elegant Epic Exotic Fabled Fancy Fearless Flaming Fluffy Forest Fuzzy
Gleaming Glossy Gothic Hasty Hazy Heavy Heroic Honest Hungry Hyper Infernal Inky Jagged Jovial Kind Legendary Liquid Little
Loyal Magic Majestic Merry Midnight Mirror Modest Moody Murky Nova Oaken Odd Orbital Paper Patient Phantom Pixel Plucky Prism
Pure Quantum Rare Rebel Royal Rugged Runic Rustic Savage Secret Serene Shy Silky Snowy Solid Sparkling Speedy Spicy Stark
Steady Sticky Stout Sturdy Sublime Sugar Supreme Tangled Tender Thorny Tidy Timber Tropic True Turquoise Ultra Unseen Urban
Vast Vivid Volcanic Wary Wicked Wooden Zany Zesty Arctic Blue Black White Cyan Lime Pink Magenta""".split()
NOUN = """Serpent Wyvern Golem Phoenix Kraken Basilisk Wraith Griffin Hydra Leviathan Dragon Drake Wolf Fox Bear Owl Hawk Falcon
Eagle Raven Crow Badger Otter Lynx Panther Tiger Lion Jaguar Cobra Viper Python Mamba Gecko Iguana Turtle Tortoise Shark Whale
Dolphin Orca Squid Octopus Crab Lobster Beetle Hornet Wasp Moth Mantis Spider Scorpion Ant Bee Cricket Firefly Dragonfly Bat
Mole Hare Rabbit Deer Stag Elk Moose Bison Yak Ox Ram Goat Boar Horse Pony Camel Llama Alpaca Zebra Rhino Hippo Elephant
Mammoth Sloth Koala Panda Lemur Monkey Gorilla Baboon Meerkat Ferret Weasel Mink Beaver Squirrel Hedgehog Raccoon Skunk Possum
Coyote Jackal Hyena Dingo Walrus Seal Penguin Puffin Pelican Heron Crane Stork Swan Goose Duck Parrot Toucan Macaw Robin
Sparrow Finch Wren Lark Magpie Jay Condor Vulture Osprey Kestrel Albatross Gull Salmon Trout Pike Carp Eel Marlin Tuna
Barracuda Piranha Seahorse Jellyfish Starfish Urchin Narwhal Manatee Frog Toad Newt Salamander Axolotl Chameleon Komodo Raptor
Rex Sphinx Chimera Minotaur Centaur Pegasus Unicorn Cyclops Titan Troll Ogre Goblin Gnome Dwarf Elf Pixie Sprite Fairy Banshee
Specter Ghoul Zombie Mummy Vampire Werewolf Yeti Sasquatch Djinn Imp Demon Angel Gargoyle Harpy Siren Mermaid Nymph Satyr
Kitsune Tengu Oni Kappa Wendigo Behemoth Colossus Juggernaut Sentinel Warden Guardian Knight Ranger Wizard Mage Sorcerer Druid
Paladin Rogue Ninja Samurai Viking Pirate Nomad Pilgrim Monk Bard Alchemist Smith Miner Engineer Pilot Captain Voyager Pioneer
Robot Android Cyborg Drone Turbine Reactor Dynamo Rocket Comet Meteor Nebula Quasar Pulsar Galaxy Lantern Anvil Hammer Beacon
Compass Glider Kite Badge Cannon Puma Cougar Ocelot Bobcat Mongoose Armadillo Pangolin Tapir Okapi Gazelle Antelope Ibex Marmot Chipmunk Hamster Gerbil Lizard Iguanodon Krait Adder Boa Herring""".split()
def uniq(seq, banned=()):
    out, seen = [], set(w.lower() for w in banned)
    for w in seq:
        if w.lower() in seen: continue
        seen.add(w.lower()); out.append(w)
    return out
adj = uniq(ADJ)
noun = uniq(NOUN, banned=adj)
print("adj", len(adj), "noun", len(noun))
if len(adj) < 256 or len(noun) < 256: sys.exit("need >=256 each")
adj, noun = adj[:256], noun[:256]
cat = {}
for a, n in itertools.product(adj, noun):
    k = (a + n).lower()
    if k in cat: sys.exit(f"ambiguous concat {a}{n} vs {cat[k]}")
    cat[k] = a + n
open("callsign_adjectives.txt", "w", newline="\n").write("\n".join(adj) + "\n")
open("callsign_nouns.txt", "w", newline="\n").write("\n".join(noun) + "\n")
print("ok")