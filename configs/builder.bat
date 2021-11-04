preferences builderpref
load configs/builder-20years.json
step 600
savechartcsvdata output/builder-1
htmlcharts output/builder-1
#
reset
load configs/builder-20years.json
set Erewhon personalTaxRate 10
set Erewhon corporateTaxRate 10
step 600
savechartcsvdata output/builder-2
htmlcharts output/builder-2
#
reset
load configs/builder-20years.json
set Erewhon personalTaxRate 5
set Erewhon corporateTaxRate 5
step 600
savechartcsvdata output/builder-3
htmlcharts output/builder-3
#
reset
load configs/builder-20years.json
set Erewhon personalTaxRate 10
set Erewhon corporateTaxRate 5
step 600
savechartcsvdata output/builder-4
htmlcharts output/builder-4
#
reset
load configs/builder-20years.json
set Erewhon personalTaxRate 5
set Erewhon corporateTaxRate 10
step 600
savechartcsvdata output/builder-5
htmlcharts output/builder-5
#
reset
load configs/builder-20years.json
set Erewhon personalTaxRate 20
set Erewhon corporateTaxRate 20
step 600
savechartcsvdata output/builder-6
htmlcharts output/builder-6
