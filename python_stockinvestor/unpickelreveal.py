import pickle
from pprint import pprint

picklerick = pickle.load(open("ITurnedMyselfIntoAPickle.p", "rb"))

pprint(picklerick)