A plugin for [RuneLite](https://github.com/runelite/runelite) which calculates and gives QOL changes for selling items to Martin Thwait.
## How to use:
- Shift right click an item within the bank or inventory to show these options and simply select your desired sell quantity per world.
<img width="469" height="139" alt="image" src="https://github.com/user-attachments/assets/e3b77a35-0ba4-48ac-89ca-4c843464903c" />


- Within the shop it will now be menu swapped [Other plugins menu swapping these items may override it.]
<img width="220" height="109" alt="image" src="https://github.com/user-attachments/assets/14f4fe1e-8a14-428f-918b-a14d1c1f9060" />


- Once you sell that item the left click will change to "Value" and stay like that until the shop no longer has that in stock.
<img width="1038" height="461" alt="image" src="https://github.com/user-attachments/assets/0fb2ee29-6995-4258-9324-459627d1669f" />

- For bank tag intergration create a bank tag called "shopscape", selected items will show in this bank tag.

## Features
- Calculates the GP value of items selected when sold to Martin Thwait
    - Has options for selling 1 , 5 , 10 , 50 , All Per world.
    - Menu swaps the selected sell quantity on the corrosponding items.
- Shows both Bank total, Inventory Total displayed at the top of your bank GUI. 
- Bank Tag intergration, creating a bank tag called "shopscape" will show all selected items within your bank for easy access.
- Highlights bank to display selected items simply with colour codes for different sell quantities.
    - The same highlight appears in the shop GUI displaying what to sell in that world
    - Checks the shop stock to avoid selling items other than Sell - All when that world has that item within stock
    - Additionally if the shop item goes away your items will rehighlight letting you know you can sell that again.
- Within config you can toggle, Shift right click options, and bank and shop highlights. A option between precise or rounded values displaying.

## Current known issues:

- Bank tag on layout mode causes you to have to disable then enable layout mode for it to update items you exclude / include. Recommended to not use when adding or   removing items. To do this right click the bank tab and disable layout.  
  
