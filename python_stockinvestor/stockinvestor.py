from logging import debug
import random as r
from dataclasses import dataclass
import signal
import pickle
from functools import partial
'''
    STATE
    public long deposit; // current bank deposit
    public long debt; // current debt
    public long shareHolding; // nb of shares held currently
    public long bidPrice, askPrice; // current bid and ask price of the stock on the market
    public boolean isBankrupt;
'''

def signal_handler_exit(stock_investor):
    print("LEEEEEEEEEET ME OUT")
    pickle.dump(open("ITurnedMyselfIntoAPickle.p", "wb"), stock_investor.sarsa_dict)
    exit()

class PythonStockInvestorAgent(object):
    def __init__(self, name):
        self.name = name
        self.last_state_action = None
        self.sarsa_dict = self._get_sarsa_dict()
        self.epsilon = 0.2
        self.alpha = 0.05
        self.gamma = 0.95
        self.actions = ["sellShares", "liquidate", "buyShares", "requestLoan", "wait"]
        self.all_states = self._initialize_states()
        return

    def _get_sarsa_dict(self):
        try:
            return pickle.load(open("ITurnedMyselfIntoAPickle.p", 'rb'))
        except FileNotFoundError:
            return dict()



    def _initialize_states(self):
        investor_states = ["bankrupt", "ownStock", "doubleStock", "noStock"]
        market_states = ["Up", "Down"]
        neutral_states = ["init", "terminate"]
        return [x + y for x in investor_states for y in market_states] + neutral_states

    # this getNextAction will be called once for each step
    def getNextAction(self, step, state):
        print("getNextAction(step = " + str(step) + ", agentid = " + str(self.name) + ", state(deposit =" + str(state.deposit) + "))")
        print(f"getNextAction(step = {step}) | agentid = {self.name}| STATE: {state.deposit}\n \
{state.debt = }\n \
{state.shareHolding = }\n \
{state.bidPrice = }\n \
{state.askPrice = }\n \
{state.isBankrupt = }")

        # possible actions
        # TODO: extend this as needed (need to change PythonStockInvestor.java accordingly)

        if self.last_state_action is None:
            last_state = last_action = None
        else:
            last_state, last_action = self.__last_state_action

        state_type = self.get_state_type_from_state(state, last_state)
        if self.last_state_action is None:
            action = self.actions[r.randrange(len(self.actions))]
        else:
            action = self.get_action_for_state_type(state_type)
            reward = self.calculateRewards(state, last_state)
            new_state_action = (state_type, action)
            self.sarsa_dict[self.last_state_action] = self.iterate_value(reward, new_state_action)
            # select a random action
            # TODO: select a reasonable action instead
        self.__last_state_action = (state_type, action)
        return action

    def get_action_for_state_type(self, state_type):
        if r.random() < self.epsilon:
            action = self.actions[r.randrange(len(self.actions))]
        else:
            action = self.get_best_action(state_type)
        return action

    def get_best_action(self, state_type):
        best_action = None
        max_reward = 0
        for action in self.actions:
            state_action_tuple = (state_type, action)
            reward = self.sarsa_dict.get(state_action_tuple, default=0)
            if reward >= max_reward:
                max_reward = reward
                best_action = action
        return best_action

    def calculateRewards(self, state, last_state):
        if state.deposit + state.shareHolding*state.bidPrice > last_state.deposit + last_state.shareHolding*last_state.bidPrice:
            return 1
        elif state.debt >= (state.deposit + state.bidPrice*state.shareHolding) and last_state.debt >= (last_state.deposit + last_state.bidPrice*last_state.shareHolding):
            return -100
        elif state.deposit + state.shareHolding*state.bidPrice < last_state.deposit + last_state.shareHolding*last_state.bidPrice:
            return -1
        else:
            return 0

    def get_state_type_from_state(self, state, last_state):
        if last_state is None:
            return "init"
        market_state = "Up" if state.askPrice > last_state.askPrice else "Down"
        investor_state = ""
        if state.debt >= (state.deposit + state.bidPrice*state.shareHolding):
            investor_state = "bankrupt"
            if last_state.debt >= (last_state.deposit + last_state.bidPrice*last_state.shareHolding):
                return "terminate"
        elif state.shareHolding > 0:
            investor_state = "doubleStock" if state.bidprice*state.shareHolding > state.deposit*2 else "ownStock"
        else:
            investor_state = "noStock"
        return investor_state + market_state

    def iterate_value(self, reward, new_state_action):
        last_q_value = self.sarsa_dict.get(self.last_state_action, 0)
        new_q_value = self.sarsa_dict.get(new_state_action, 0)
        return last_q_value + self.alpha * (reward + self.gamma * new_q_value - last_q_value)

####################################################
## this class is just a proxy that calls getNextAction of each individual agent
class PythonStockInvestor(object):
    def __init__(self):
        self.agents = {}
        return
    
    def getNextAction(self, step, agentid, state):
        # create the agent if it is not know yet
        if not agentid in self.agents:
            self.agents[agentid] = PythonStockInvestorAgent(agentid)
        return self.agents[agentid].getNextAction(step, state)

    class Java:
        implements = ["core.MyStockInvestor$IPythonStockInvestor"]

####################################################
# This code sets up the connection to the Java side
from py4j.clientserver import ClientServer, JavaParameters, PythonParameters
from py4j.java_gateway import java_import


pythonstockinvestor = PythonStockInvestor()
signal.signal(signal.SIGINT, partial(signal_handler_exit ,pythonstockinvestor))
gateway = ClientServer(
    java_parameters=JavaParameters(
        port=25333,
        auto_field=True
    ),
    python_parameters=PythonParameters(),
    python_server_entry_point=pythonstockinvestor)
java_import(gateway.jvm,'core.MyStockInvestor$State')
print("gateway started")
######################################################

# do other stuff here

