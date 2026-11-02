---
navigation:
  title: Generator, Smelter and Reactor
  icon: combustion_generator
  parent: screens/index.md
  position: 11
---

# The screens of power

Use a Combustion Generator, a Boiler, a Smelter or a Reactor Core to open them. [Power](../power.md) tells what they make and use.

<residual:Clip name="smelter" alt="A controller in the Smelter's slot, holding its melt in the band" />

<residual:Steps>
  <residual:Step>
    A **Combustion Generator**: fuel under its flame.

    <residual:Shot name="power_generator" alt="A Combustion Generator burning coal">
      <residual:Part name="fuel">
        **Fuel**: What a furnace burns.
      </residual:Part>
      <residual:Part name="flame">
        **The flame**: Burns down with the fuel.
      </residual:Part>
      <residual:Part name="power">
        **Stored power**: Point at it for how much, and what it makes.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Smelter**: ore on the left, ingots on the right.

    <residual:Shot name="power_smelter" alt="A Smelter's screen">
      <residual:Part name="ore">
        **Ore**
      </residual:Part>
      <residual:Part name="work">
        **The work**: Fills as it smelts. With a controller, it shows the melt: a column a look, the band shaded across.
      </residual:Part>
      <residual:Part name="ingots">
        **Ingots**
      </residual:Part>
      <residual:Part name="power">
        **Stored power**: Point at it for how much, and what it draws.
      </residual:Part>
      <residual:Part name="controller">
        **Controller**: A model practised on Control. It holds each melt in its band, and a melt it holds makes three ingots instead of two.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Reactor Core**: fuel under its flame, and its cycle look by look.

    <residual:Shot name="power_reactor" alt="A Reactor Core's screen">
      <residual:Part name="fuel">
        **Fuel**: Reactor Fuel.
      </residual:Part>
      <residual:Part name="flame">
        **The flame**: Burns down with the fuel.
      </residual:Part>
      <residual:Part name="cycle">
        **The cycle**: A column a look, the band shaded across. Point at it for why it is not running, or how far through the cycle it is and how much of the last one was held.
      </residual:Part>
      <residual:Part name="power">
        **Stored power**: Point at it for how much, and what it makes.
      </residual:Part>
      <residual:Part name="controller">
        **Controller**: A model that passes Containment. Nothing else starts it, and it stops itself rather than go on without one.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
