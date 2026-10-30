# Two scenes a row, each 33 east of the last, its plot's width and reach, and each row 16 blocks north of the last.
function gallery_wide:gallery/gold
execute positioned ~33 ~ ~ run function gallery_wide:gallery/iron
execute positioned ~ ~ ~-16 run function gallery_wide:gallery/diamond
