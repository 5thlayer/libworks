# Two scenes a row, each row 16 blocks north of the last.
function gallery_rows:gallery/gold
execute positioned ~24 ~ ~ run function gallery_rows:gallery/iron
execute positioned ~ ~ ~-16 run function gallery_rows:gallery/diamond
