import os

mywife = 'C:\\Users\\35477\\OneDrive\\Desktop\\HR\\Onn_5\\velraent_gagnanam\\Project 2\\Threadneedle-dev'

for i in range(100):
    print(os.getcwd())
    os.system(f"{mywife}\\build --charts --cl --gui --b=configs/dl_stocksim.batch")
     