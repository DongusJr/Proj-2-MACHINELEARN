import csv
import sys
import os

csvfiles = {}

for f in os.listdir(os.getcwd()):
    if f.endswith(".csv"):
       with open(f, 'rb') as fptr:
          csvfiles[f] = list(fptr)


for line in csvfiles:
    for n in csvfiles[line]:
        print n,
       
