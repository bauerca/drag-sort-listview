from matplotlib.lines import Line2D
from matplotlib.text import Text
from matplotlib.patches import Rectangle
import pylab
from xml.etree.ElementTree import ElementTree



class State:
  
  xbuff = 40
  ybuff = 100

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
    self.width = 100
    self.height = int(element.find("ViewHeight").text)
    self.dstate = int(element.find("DragState").text)
    self.lasty = int(element.find("LastY").text)
    self.floaty = int(element.find("FloatY").text)
  
  def draw(self, axes):
    axes.clear()

    axes.set_ylim((-self.ybuff - self.height, self.ybuff))
    axes.set_xlim((-self.xbuff, self.width + self.xbuff))

    for i in range(self.count):
      top = -self.tops[i]
      bottom = -self.bottoms[i]

      axes.add_artist(Text(self.width/2, (top+bottom)/2,
        str(self.positions[i]), va='center', ha='center'))

      if self.positions[i] == self.src:
        axes.add_patch(Rectangle((0,bottom), self.width, top-bottom,
          fill=True, ec=None, fc='0.7'))
      elif self.positions[i] == self.exp1:
        if self.exp1 < self.src:
          gap_bottom = top - self.exp1_gap
        else:
          gap_bottom = bottom
        axes.add_patch(Rectangle((0,gap_bottom), self.width, self.exp1_gap,
          fill=True, ec=None, fc='0.7'))
      elif self.positions[i] == self.exp2:
        if self.exp2 < self.src:
          gap_bottom = top - self.exp2_gap
        else:
          gap_bottom = bottom
        axes.add_patch(Rectangle((0,gap_bottom), self.width, self.exp2_gap,
          fill=True, ec=None, fc='0.7'))

      #print "drawing line"
      axes.add_line(Line2D([0,self.width], [top, top], c='b'))
      axes.add_line(Line2D([0,self.width], [bottom, bottom], c='b'))
      #axes.plot([0,self.width], [top, top], 'k')
      #axes.plot([0,self.width], [bottom, bottom], 'k')

    for edge in self.edges:
      axes.add_line(Line2D([0,self.width], [-edge, -edge], c='g'))
      

    axes.add_line(Line2D([0,self.width], [-self.floaty,-self.floaty], c='r', lw=2))
    #axes.add_line(Line2D([0,self.width], [-self.lasty,-self.lasty], c='r', lw=2))

    axes.add_patch(Rectangle((0, -self.height), self.width, self.height,
      fill=False, ls='dashed', ec='0.7'))


class StateFlipper:
  def __init__(self, states, startFrame=0):
    self.states = states

    if startFrame < 0:
      self.curr = len(states) - 1
    else:
      self.curr = startFrame

    # make axes
    self.fig = pylab.figure()
    self.ax = self.fig.add_subplot(111)
    self.ax.set_aspect('equal')
    self.fig.canvas.mpl_connect('key_press_event', self.flip)

    states[self.curr].draw(self.ax)
    pylab.show()

  def flip(self, event):
    #print event.key
    if event.key == 'right':
      self.curr += 1
    elif event.key == 'left':
      self.curr -= 1
    else:
      return

    #self.ax.clear()
    self.states[self.curr].draw(self.ax)
    self.fig.canvas.draw()


def getStates(file):
  tree = ElementTree();
  tree.parse(file);
  root = tree.getroot()

  return map(State, list(root.iter("DSLVState")))
  

if __name__ == "__main__":
  states = getStates("dslv_state.txt")
  StateFlipper(states, startFrame=-1)
