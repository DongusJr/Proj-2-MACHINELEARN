load configs/stock_base2.json
set seed

addagent StockExchange Bank-4 name=TSE seats=10 verbose=

addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40
addagent StockMarket Bank-4 exchange=TSE ipo=40

addagent InvestmentCompany Bank-4 exchange=TSE initialDeposit=500 strategy=DEFAULT offeredSalary=0
addagent InvestmentCompany Bank-4 exchange=TSE initialDeposit=500 strategy=DEFAULT offeredSalary=0
addagent InvestmentCompany Bank-4 exchange=TSE initialDeposit=500 strategy=RANDOM offeredSalary=0
addagent InvestmentCompany Bank-4 exchange=TSE initialDeposit=500 strategy=RANDOM offeredSalary=0
addagent InvestmentCompany Bank-4 exchange=TSE initialDeposit=500 strategy=RANDOM offeredSalary=0

repeat 50 addagent StockInvestor Bank-4

setbaserate 1
set maxdatapoints 2000
steps 2000
wait 700

htmlsetup /tmp/TN_testrunner/data_gen/interest1_10s_5p_1e_92
htmlcharts /tmp/TN_testrunner/data_gen/interest1_10s_5p_1e_92
savechartcsvdata /tmp/TN_testrunner/data_gen/interest1_10s_5p_1e_92
exit

