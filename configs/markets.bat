load configs/2C20W111.json 
set seed 123456
steps 1000
htmlcharts results/2C20W111 "Control" 
reset
wait 1000
load configs/2C20W111-2.json
set seed 123456
steps 1000
htmlcharts results/2C20W111-2
reset
wait 1000
load configs/2C20W121.json
set seed 123456
steps 1000
htmlcharts results/2C20W121
reset
wait 1000
load configs/2C20W121-2.json
set seed 123456
steps 1000
htmlcharts results/2C20W121-2
reset
wait 1000
load configs/2C20W151.json
set seed 123456
steps 1000
htmlcharts results/2C20W151
reset
wait 1000
load configs/2C20W151-2.json
set seed 123456
steps 1000
htmlcharts results/2C20W151-2
quit
