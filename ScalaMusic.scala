import scala.math._
import music._
import music.midi._
import Pitch._
import Instrument._

abstract class Music
case class Note(duration: Double, pitch: Pitch, instr: Instrument) extends Music
case class Rest(duration: Double) extends Music
case class Concat(m1: Music, m2: Music) extends Music
case class Together(m1: Music, m2: Music) extends Music
case class Forever(m: Music) extends Music

def musicIn(m: music.Music): Music =
    m.accept(new Music.Visitor[Music]() {
       def on(m: music.Note) = Note(m.duration, m.pitch, m.instrument)
       def on(m: music.Rest) = Rest(m.duration)
       def on(m: music.Concat) = Concat(m.first.accept(this), m.second.accept(this))
       def on(m: music.Together) = Together(m.top.accept(this), m.bottom.accept(this))
       def on(m: music.Forever) = Forever(m.loop.accept(this))
    })

def musicOut(m: Music): music.Music =
    m match {
       case Note(duration, pitch, instr) => new music.Note(duration, pitch, instr)
       case Rest(duration) => new music.Rest(duration)
       case Concat(m1, m2) => new music.Concat(musicOut(m1), musicOut(m2))
       case Together(m1, m2) => new music.Together(musicOut(m1), musicOut(m2))
       case Forever(m) => new music.Forever(musicOut(m))
    }

def notes(notes: String, instr: Instrument): Music = musicIn(MusicLanguage.notes(notes, instr))

var player = new MusicPlayer()

def play(m: Music) = {
    player = new MusicPlayer()
    player.play(musicOut(m))
}

def rrryb = notes("C C C3/4 D/4 E | E3/4 D/4 E3/4 F/4 G2 | C'/3 C'/3 C'/3 G/3 G/3 G/3 E/3 E/3 E/3 C/3 C/3 C/3 | G3/4 F/4 E3/4 D/4 C2", PIANO)

def pachelbelMelody = notes("^F'2 E'2 | D'2 ^C'2 | B2 A2 | B2 ^C'2 | D'2 ^C'2 | B2 A2 | G2 ^F2 | G2 E2 | D ^F A G | ^F D ^F E | D B, D A | G B A G | ^F D E ^C' | D' ^F' A' A | B G A ^F | D D' D3/2 .1/2 |", VIOLIN)

def pachelbelBass = notes("D,2 A,,2 | B,,2 ^F,,2 | G,,2 D,,2 | G,,2 A,,2", CELLO)

def duration(m : Music): Double =
    m match {
      case Note(duration, _, _) => duration
      case Rest(duration) => duration
      case Concat(m1, m2) => duration(m1) + duration(m2)
      case Together(m1, m2) => max(duration(m1), duration(m2))
      case Forever(_) => Double.PositiveInfinity
    }

def transpose(m: Music, n: Int): Music =
    m match {
      case Note(duration, pitch, instr) => Note(duration, pitch.transpose(n), instr)
      case Rest(_) => m
      case Concat(m1, m2) => Concat(transpose(m1, n), transpose(m2, n))
      case Together(m1, m2) => Together(transpose(m1, n), transpose(m2, n))
      case Forever(m) => Forever(transpose(m, n))
    }

def delay(m: Music, dur: Double): Music = Concat(Rest(dur), m)

val twoRounds = Together(rrryb, delay(rrryb, 4))

def canon(m: Music, dur: Double, n: Int): Music =
    if (n == 1) {
      m
    } else {
      Together(m, canon(delay(m, dur), dur, n-1))
    }

def canon(m: Music, dur: Double, n: Int, f: Music => Music): Music =
    if (n == 1) {
      m
    } else {
      Together(m, canon(delay(f(m), dur), dur, n-1, f))
    }

def transposer(semitones: Int) = (m:Music) => transpose(m, semitones)

def fourRounds = canon(rrryb, 4, 4, transposer(OCTAVE))

def counterpoint(m: Music, f: Music => Music, n: Int): Music =
  if (n == 1) {
    m
  } else {
    Together(m, counterpoint(f(m), f, n-1))
  }

def delayer(d: Double) = (m:Music) => delay(m, d)

def canon(m: Music, dur: Double, n: Int, f: Music => Music) =
  counterpoint(m, f.compose(delayer(dur)), n)

def series[T](initial: T, combine: (T, T) => T, change: T => T, n: Int): T =
  if (n == 1) {
    initial
  } else {
    combine(initial, series(change(initial), combine, change, n-1))
  }

def counterpoint(m: Music, f: Music => Music, n: Int) =
  series(m, Together, f, n)

def repeat(m: Music, n: Int, f: Music => Music) =
  series(m, Concat, f, n)

def infiniteRounds = canon(Forever(rrryb), 4, 4, transposer(OCTAVE))

def accompany(m: Music, b: Music) =
  if (duration(m) == Double.PositiveInfinity) {
    Together(m, Forever(b))
  } else {
    Together(m, repeat(b, (duration(m)/duration(b)).toInt, identity))
  }

def pachelbelCanon = canon(Forever(pachelbelMelody), 3, 16, identity)

def pachelbel = Concat(pachelbelBass, accompany(pachelbelCanon, pachelbelBass))
