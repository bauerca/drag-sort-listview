from matplotlib.lines import Line2D
from matplotlib.text import Text
from matplotlib.patches import Rectangle
import pylab
from xml.etree.ElementTree import ElementTree

width = 300

class ItemArtist:

  def __init__(self, position, state):
    self.position = position
    
    indx = state.positions.index(position)

    self.top = -state.tops[indx]
    self.top_line, = pylab.plot([0,width], 2*[self.top], c='b')

    self.bottom = -state.bottoms[indx]
    self.bottom_line, = pylab.plot([0,width], 2*[self.bottom], c='b')

    self.edge = -state.edges[indx]
    self.edge_line, = pylab.plot([0,width], 2*[self.edge], c='g')

    self.label = Text(width/2, (self.top+self.bottom)/2,
        str(position), va='center', ha='center')

    self.axes = pylab.gca()
    self.axes.add_artist(self.label)

    self.src_box = None
    self.exp_box = None
    self._check_boxes(state)


  def _check_boxes(self, state):

    if self.position == state.src:
      if self.src_box == None:
        self.src_box = Rectangle((0, self.bottom), width,
          self.top - self.bottom, fill=True, ec=None, fc='0.7')
        self.axes.add_patch(self.src_box)
      else:
        self.src_box.set_y(self.bottom)
        self.src_box.set_height(self.top - self.bottom)

    elif self.position == state.exp1:
      if state.exp1 < state.src:
        gap_bottom = self.top - state.exp1_gap
      else:
        gap_bottom = self.bottom

      if self.exp_box == None:
        self.exp_box = Rectangle((0,gap_bottom), width,
          state.exp1_gap, fill=True, ec=None, fc='0.7')
        self.axes.add_patch(self.exp_box)
      else:
        self.exp_box.set_y(gap_bottom)
        self.exp_box.set_height(state.exp1_gap)

    elif self.position == state.exp2:
      if state.exp2 < state.src:
        gap_bottom = self.top - state.exp2_gap
      else:
        gap_bottom = self.bottom

      if self.exp_box == None:
        self.exp_box = Rectangle((0,gap_bottom), width, state.exp2_gap,
          fill=True, ec=None, fc='0.7')
        self.axes.add_patch(self.exp_box)
      else:
        self.exp_box.set_y(gap_bottom)
        self.exp_box.set_height(state.exp2_gap)
    else:
      if self.src_box != None:
        self.src_box.remove()
        self.src_box = None
      if self.exp_box != None:
        self.exp_box.remove()
        self.exp_box = None


  def inState(self, state):
    return self.position in state.positions

  def update(self, position, state):
    moved = False

    if position != self.position:
      self.position = position
      self.label.set_text(str(position))

    indx = state.positions.index(self.position)

    old_top = self.top
    self.top = -state.tops[indx]
    if old_top != self.top:
      self.top_line.set_ydata(2*[self.top])
      moved = True

    old_bottom = self.bottom
    self.bottom = -state.bottoms[indx]
    if old_bottom != self.bottom:
      self.bottom_line.set_ydata(2*[self.bottom])
      moved = True

    old_edge = self.edge
    self.edge = -state.edges[indx]
    if old_edge != self.edge:
      self.edge_line.set_ydata(2*[self.edge])
    
    if moved:
      # adjust label, blank spot, etc.
      self.label.set_y((self.top + self.bottom)/2)
      self._check_boxes(state)

  def remove(self):
    self.edge_line.remove()
    self.top_line.remove()
    self.bottom_line.remove()
    self.label.remove()

    if self.src_box != None:
      self.src_box.remove()
    if self.exp_box != None:
      self.exp_box.remove()


class StateArtist:
  xbuff = 40
  ybuff = 100

  def __init__(self, state):

    self.fig = pylab.figure(figsize=(5,9))
    self.axes = self.fig.add_subplot(111)
    self.axes.set_aspect('equal')

    self.axes.set_ylim((-self.ybuff - state.height, self.ybuff))
    self.axes.set_xlim((-self.xbuff, width + self.xbuff))

    self.float_y = -state.float_y
    self.float_y_line, = pylab.plot([0,width], 2*[self.float_y],
      c='r', lw=2)

    self.items = []
    self.update(state)

    self.axes.add_patch(Rectangle((0, -state.height), width, state.height,
      fill=False, ls='dashed', ec='0.7'))

  def update(self, state):

    # update floatView location
    old_float_y = self.float_y
    self.float_y = -state.float_y
    if old_float_y != self.float_y:
      self.float_y_line.set_ydata(2*[self.float_y])

    updatedPos = []
    toRecycle = []
    for item in self.items:
      if item.inState(state):
        item.update(item.position, state)
        updatedPos.append(item.position)
      else:
        toRecycle.append(item)

    posSet = set(state.positions)
    updatedPosSet = set(updatedPos)

    unupdatedPosSet = posSet.symmetric_difference(updatedPosSet)
    for position in unupdatedPosSet:
      if len(toRecycle) != 0:
        item = toRecycle.pop(-1)
        item.update(position, state)
      else:
        item = ItemArtist(position, state)
        self.items.append(item)

    if len(toRecycle) != 0:
      for item in toRecycle:
        item.remove() #remove artists from current plot
        self.items.remove(item)

    self.fig.canvas.draw()

class State:

  def __init__(self, element):
    self.positions = map(int, element.find("Positions").text.split(",")[:-1])
    self.tops = map(int, element.find("Tops").text.split(",")[:-1])
    self.bottoms = map(int, element.find("Bottoms").text.split(",")[:-1])
    self.count = len(self.positions)
    self.edges = map(int, element.find("ShuffleEdges").text.split(",")[:-1])

    self.src = int(element.find("SrcPos").text)
    self.src_h = int(element.find("SrcHeight").text)
    self.exp1 = int(element.find("FirstExpPos").text)
    self.exp1_gap = int(element.find("FirstExpBlankHeight").text)
    self.exp2 = int(element.find("SecondExpPos").text)
    self.exp2_gap = int(element.find("SecondExpBlankHeight").text)
    self.height = int(element.find("ViewHeight").text)
    self.lasty = int(element.find("LastY").text)
    self.float_y = int(element.find("FloatY").text)
  


class StateAnimator:
  page_frames = 30

  def __init__(self, states, startFrame=0):
    self.states = states
    self.count = len(states)

    if startFrame < 0 or startFrame >= self.count:
      self.curr = self.count - 1
    else:
      self.curr = startFrame

    self.state_artist = StateArtist(self.states[self.curr])
    self.state_artist.fig.canvas.mpl_connect('key_press_event', self.flip)

    pylab.show()

  def flip(self, event):
    #print event.key
    if event.key == 'right':
      self.curr += 1
    elif event.key == 'left':
      self.curr -= 1
    elif event.key == 'up':
      self.curr -= self.page_frames
    elif event.key == 'down':
      self.curr += self.page_frames
    else:
      return

    if self.curr >= self.count:
      print "reached end of saved motions"
      self.curr = self.count - 1
    elif self.curr < 0:
      print "reached beginning of saved motions"
      self.curr = 0
    else:
      print "flipped to frame " + str(self.curr)
      self.state_artist.update(self.states[self.curr])
      

    #self.ax.clear()



def getStates(file):
  tree = ElementTree();
  tree.parse(file);
  root = tree.getroot()

  return map(State, list(root.iter("DSLVState")))
  

if __name__ == "__main__":
  states = getStates("dslv_state.txt")
  StateAnimator(states, startFrame=-1)
