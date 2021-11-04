load configs/builder-20years.json
step 1200
savechartcsvdata output/builder-1
htmlcharts output/builder-1
reset
#
load configs/builder-20years.json
set Erewhon personalTaxRate 10
set Erewhon corporateTaxRate 10
step 1200
savechartcsvdata output/builder-2
htmlcharts output/builder-2
