fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

setblock ~13 ~ ~-6 minecraft:hopper[facing=south]
setblock ~13 ~1 ~-6 minecraft:chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:64}]}
setblock ~13 ~ ~-5 minecraft:chest[facing=south]

setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"libworks.gallery.hopper"},{translate:"libworks.gallery.hopper.1"},"",""]}}
