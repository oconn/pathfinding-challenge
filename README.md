## Pathfinding with Wombats.io

### Wombats overview

Wombats is a multi-player programming game where users submit code (Wombat algorithms) that gets run against one another in an effort to achieve the highest score.

![image-one](/resources/arena-image.png)

There are a lot of obstacles and items in a Wombat's arena

    - food
    - poison
    - wombats
    - NPCs
    - open space
    - wood walls
    - steel walls
    - smoke
    - fog

The way the game works is as follows

  1) Users receive input from the server about the current state of the world from the perspective of the wombat.
  1) That input gets feed into a user defined algorithm for users to make decisions with.
  1) The user defined algorithm returns a command and optional state that will be returned back to the user on the next frame.
  1) The server performs calculations and conflict resolution using all the provided actions to update the state of the game.
  1) Repeat

Each player is only able to see a partial view of the arena and their vision is obscured by obstacles like walls. So for example, the play pictured above would really see this.

![image-two](/resources/arena-image.png)

### Challenge overview

You goal is to;

1) Analyze the input data `resources/arena.json`
2) Formulate a plan to collect food
3) Talk through a few possible solutions for implementing this idea
4) Write some code to start parsing the input

This is a collaborative exercise so feel free to ask questions that you would typically ask while working with a team to solve problems and use any resource at your disposal to best solve this challenge.
