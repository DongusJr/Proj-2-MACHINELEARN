load configs/1000workers.json
step 6000
htmlcharts results/1000w-baseline "1000 Workers Baseline"
reset
#
load configs/1000workers.json
set Erewhon payUnemployment false
set Erewhon personalTaxRate  0
set Erewhon corporateTaxRate 0 
set Erewhon corporateCutoff  0  
set Erewhon personalCutoff  0  
set Erewhon unemploymentPayment 0
step 3000
printmoney Farm-1012 20000
printmoney Farm-1017 20000
printmoney Farm-1015 20000
step 3000
htmlcharts results/1000w-printmoney-1 "1000 Workers - money @ 3000"
reset
#
#
load configs/1000workers.json
set Erewhon unemploymentPayment 2
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 10
set Erewhon corporateCutoff  500
step 6000
htmlcharts results/1000w-unemployment-1 "1000 Workers - unemployment 2, 10% Company"
reset
#
load configs/1000workers.json
set Erewhon unemploymentPayment 2
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 20
set Erewhon corporateCutoff  500
step 6000
htmlcharts results/1000w-unemployment-2 "1000 Workers - 20% Company"
reset
#
load configs/1000workers.json
set Erewhon unemploymentPayment 2
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 30
set Erewhon corporateCutoff  500
step 6000
htmlcharts results/1000w-unemployment-3 "1000 Workers - 30% Company"
reset
#
load configs/1000workers.json
set Erewhon unemploymentPayment 4
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 40
set Erewhon corporateCutoff  500
step 6000
htmlcharts results/1000w-unemployment-4 "1000 Workers - 40% Company"
reset
#
#
load configs/1000workers.json
set Erewhon unemploymentPayment 4
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 50
set Erewhon corporateCutoff  500
step 6000
htmlcharts results/1000w-unemployment-5 "1000 Workers - 50% Company"
reset
#
#
load configs/1000workers.json
set Erewhon unemploymentPayment 4
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 10
set Erewhon corporateCutoff  500
set Erewhon personalTaxRate 10
set Erewhon personalCutoff  20 

step 6000
htmlcharts results/1000w-unemployment-6 "1000 Workers - 10% Company 10% Personal"
reset
#
#
load configs/1000workers.json
set Erewhon unemploymentPayment 4
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 20
set Erewhon corporateCutoff  500
set Erewhon personalTaxRate 20
set Erewhon personalCutoff  20 

step 6000
htmlcharts results/1000w-unemployment-7 "1000 Workers - 20% Company 20% Personal"
reset
#
#
load configs/1000workers.json
set Erewhon unemploymentPayment 4
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 30
set Erewhon corporateCutoff  500
set Erewhon personalTaxRate 20
set Erewhon personalCutoff  20 

step 6000
htmlcharts results/1000w-unemployment-8 "1000 Workers - 20% Company 30% Personal"
reset
#
#
load configs/1000workers.json
set Erewhon unemploymentPayment 4
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 40
set Erewhon corporateCutoff  500
set Erewhon personalTaxRate 20
set Erewhon personalCutoff  20 

step 6000
htmlcharts results/1000w-unemployment-9 "1000 Workers - 20% Company 40% Personal"
reset
#
load configs/1000workers.json
set Erewhon unemploymentPayment 4
set Erewhon payUnemployment true
set Erewhon corporateTaxRate 20
set Erewhon corporateCutoff  500
set Erewhon personalTaxRate 40
set Erewhon personalCutoff  20 

step 6000
htmlcharts results/1000w-unemployment-10 "1000 Workers - 40% Company 20% Personal"
reset
#
#


