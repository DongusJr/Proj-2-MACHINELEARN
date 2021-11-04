load configs/100workers.json
step 6000
htmlcharts results/100w-baseline "100 Workers"
reset
#
#
load configs/100workers.json
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 50
step 6000
htmlcharts results/100w-unemployment "100 Workers - unemployment, 50% Company"
reset
#
#
load configs/100workers.json
set M-Milk payDividend true
set M-Food payDividend true
step 6000
htmlcharts results/100w-dividend "100 Workers - Market Dividend"
reset
