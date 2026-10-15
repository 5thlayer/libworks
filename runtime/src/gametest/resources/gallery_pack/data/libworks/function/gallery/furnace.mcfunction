# structure_void first: a fill that changes nothing fails the build (ADR 0010).
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

setblock ~13 ~ ~-6 minecraft:furnace[facing=south]{Items:[{Slot:0b,id:"minecraft:raw_iron",count:16},{Slot:1b,id:"minecraft:coal",count:4}]}

setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"libworks.gallery.furnace"},{translate:"libworks.gallery.furnace.1"},"",""]}}
