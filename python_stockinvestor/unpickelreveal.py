import pickle
from pprint import pprint

picklerick = pickle.load(open("SarsaDict_PythonStockInvestor-17.p", "rb"))
infolist = pickle.load(open("InfoList_PythonStockInvestor-17.p", 'rb'))

pprint(picklerick)
print(len(infolist), max(infolist, key=lambda x: x[0]))