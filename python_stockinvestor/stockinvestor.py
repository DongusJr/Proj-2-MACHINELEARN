from logging import debug
import random as r
import pickle
import os

'''
    STATE
    public long deposit; // current bank deposit
    public long debt; // current debt
    public long shareHolding; // nb of shares held currently
    public long bidPrice, askPrice; // current bid and ask price of the stock on the market
    public boolean isZombie;
'''
STATEGAP = 5

class PythonStockInvestorAgent(object):
    def __init__(self, name):
        self._initialize_agent(name)
        
    def _initialize_agent(self, name):
        self.pickle_file_name = f"SarsaDict_{name}.p"
        self.info_list_name = f"InfoList_{name}.p"
        self.name = name
        self.last_state_action = None
        self.last_state_type = None
        self.initial_deposit = None
        self.sarsa_dict = self._get_sarsa_dict()
        self.info_list = self._get_info_list()
        self.max_profit = 0
        self.epsilon = 0.2
        self.alpha = 0.1
        self.gamma = 0.9998
        self.actions = ["sellShares", "liquidate", "buyShares", "requestLoan", "wait"]
        self.all_states = self._initialize_states()

    def _get_sarsa_dict(self):
        try:
            with open(self.pickle_file_name, 'rb+') as pfile:
                if os.path.getsize(self.pickle_file_name) > 0:
                    return pickle.load(pfile)
                else:
                    raise FileNotFoundError
        except FileNotFoundError:
            return dict()

    def _get_info_list(self):
        try:
            with open(self.info_list_name, 'rb+') as pfile:
                if os.path.getsize(self.info_list_name) > 0:
                    return pickle.load(pfile)
                else:
                    raise FileNotFoundError
        except FileNotFoundError:
            return []

    def _pickle_results(self):
        with open(self.pickle_file_name, "wb+") as pfile:
            pickle.dump(self.sarsa_dict, pfile)
        with open(self.info_list_name, "wb+") as info_list_file:
            pickle.dump(self.info_list, info_list_file)

    def _initialize_states(self):
        investor_states = ["bankrupt", "ownStock", "doubleStock", "noStock"]
        market_states = [f"{i*STATEGAP}~{(i+1)*STATEGAP}" for i in range(-20, 20)]
        edge_market_states = [">+100", "<-100"]
        market_states += edge_market_states
        neutral_states = ["init", "terminate"]
        return [x + y for x in investor_states for y in market_states] + neutral_states

    def _display_state(self, step, state):
        print(f"getNextAction(step = {step}) | agentid = {self.name}| STATE: {state.deposit}\n \
{state.debt = }\n \
{state.shareHolding = }\n \
{state.bidPrice = }\n \
{state.askPrice = }\n \
{state.isZombie = }")

    # this getNextAction will be called once for each step
    def getNextAction(self, step, state):
        if self.initial_deposit is None:
            self.initial_deposit = state.deposit
        self._display_state(step, state)
        print("getNextAction(step = " + str(step) + ", agentid = " + str(self.name) + ", state(deposit =" + str(state.deposit) + "))")



        # possible actions
        # TODO: extend this as needed (need to change PythonStockInvestor.java accordingly)

        if self.last_state_action is None:
            last_state = last_action = None
        else:
            last_state, last_action = self.last_state_action

        state_type = self.get_state_type_from_state(state, last_state)
        if self.last_state_action is None:
            action = self.actions[r.randrange(len(self.actions))]
        else:
            self.max_profit = max(self.max_profit, state.deposit - self.initial_deposit)
            action = self.get_action_for_state_type(state_type)
            if action is None:
                print("ACTION IS NONE WHAT IS GOING ON HERE?")
                print(f"{state =}, {last_state =}, {state_type =}, {self.last_state_type =}")
            reward = self.calculateRewards(state, last_state)
            new_state_action = (state_type, action)
            self.sarsa_dict[(self.last_state_type, last_action)] = self.iterate_value(reward, new_state_action)
            # select a random action
            # TODO: select a reasonable action instead
        self.last_state_action = (state, action)
        self.last_state_type = state_type
        if (state.isZombie):
            self.info_list.append((str(step), self.max_profit, state.deposit, state.debt))
            self._pickle_results()
            self._initialize_agent(self.name)
        return action

    def get_action_for_state_type(self, state_type):
        if r.random() < self.epsilon:
            action = self.actions[r.randrange(len(self.actions))]
        else:
            action = self.get_best_action(state_type)
        return action

    def get_best_action(self, state_type):
        best_action = None
        max_reward = float('-inf')
        for action in self.actions:
            state_action_tuple = (state_type, action)
            reward = self.sarsa_dict.get(state_action_tuple, 0)
            if reward != 0 and reward > max_reward:
                max_reward = reward
                best_action = action
        if best_action is not None:
            return best_action
        else:
            return self.actions[r.randrange(len(self.actions))]

    def calculateRewards(self, state, last_state):
        # if int(state.deposit) + int(state.shareHolding)*int(state.bidPrice) > int(last_state.deposit) + int(last_state.shareHolding)*int(last_state.bidPrice):
        #     return (int(state.deposit) + int(state.shareHolding)) - (int(last_state.deposit) + int(last_state.deposit))*0.99
        # elif int(state.debt) >= (int(state.deposit) + int(state.bidPrice)*int(state.shareHolding)) and int(last_state.debt) >= (int(last_state.deposit) + int(last_state.bidPrice)*int(last_state.shareHolding)):
        #     return -100
        # elif int(state.deposit) + int(state.shareHolding)*int(state.bidPrice) < int(last_state.deposit) + int(last_state.shareHolding)*int(last_state.bidPrice):
        #     return (int(state.deposit) + int(state.shareHolding)) - (int(last_state.deposit) + int(last_state.deposit))*0.01
        # else:
        #     return 0
        reward = 0
        networth_now = state.deposit - state.debt
        networth_before = last_state.deposit - last_state.debt
        difference = (networth_now - networth_before)
        reward += (state.shareHolding//2) + difference
        return reward


    def get_state_type_from_state(self, state, last_state):
        if last_state is None or last_state.isZombie:
            return "init"
        difference = int(state.askPrice) - int(last_state.askPrice)
        if abs(difference) > 100:
            market_state = ">+100" if difference > 0 else "<-100"
        else:
            market_state = f"{(difference//STATEGAP)*STATEGAP}~{(difference//STATEGAP)*STATEGAP + 5}"
        investor_state = ""
        if int(state.debt) >= (int(state.deposit) + int(state.bidPrice)*int(state.shareHolding)):
            investor_state = "bankrupt"
            if state.isZombie:
                return "terminate"
        elif int(state.shareHolding) > 0:
            investor_state = "doubleStock" if int(state.bidPrice)*int(state.shareHolding) > int(state.deposit)*2 else "ownStock"
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

