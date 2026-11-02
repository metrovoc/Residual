---
navigation:
  title: Hall Console
  icon: hall_console
  parent: screens/index.md
  position: 26
---

# A Hall Console's screen

Use a Hall Console to open it. [Halls](../datacenter.md) tells what makes a hall.

One display: what the hall is, what it is doing, and what the largest run it could hold would cost.

<residual:Shot name="hall_console_reading" alt="A Hall Console's screen">
  <residual:Part name="reading">
    **The hall**: Its racks, pods and devices, what it is rated for, what it is running, and what it draws. Point at the first line for what it still lacks, at the power for what PUE is.
  </residual:Part>
  <residual:Part name="largest">
    **The largest run**: A prediction, by the sums: six flops a parameter a token, sixteen bytes a parameter, at its whole rated speed.
  </residual:Part>
  <residual:Part name="racks">
    **Its racks**: A column for each rack, as tall as that rack is busy.
  </residual:Part>
</residual:Shot>
