package org.shomah4a.DigitalClockWallpaper

import android.graphics
import android.os
import android.service.wallpaper
import android.util
import android.view
import android.content

import java.text



class BatteryReceiver extends content.BroadcastReceiver
{
  var batteryLevel = 0
  var batteryStatus = os.BatteryManager.BATTERY_STATUS_UNKNOWN

  val statusTable = Map(os.BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unknown",
                        os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging",
                        os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging",
                        os.BatteryManager.BATTERY_STATUS_FULL -> "Full")

  override def onReceive(context: content.Context, intent: content.Intent)
  {
    val action = intent.getAction

    action match
    {
      case content.Intent.ACTION_BATTERY_CHANGED =>
        {
          val level = intent.getIntExtra("level", 0)
          this.batteryLevel = level
          this.batteryStatus = intent.getIntExtra("status", 0)
        }
    }
  }

  def getBatteryLevel() = this.batteryLevel

  def getBatteryStatus() = this.statusTable.getOrElse(this.batteryStatus, "Unknown")
}



class DigitalClockWallpaper extends wallpaper.WallpaperService
{
  private val TAG = "DCW"

  private val handler = new os.Handler()

  override def onCreate() =
    {
      super.onCreate()
    }

  override def onDestroy() = super.onDestroy()

  override def onCreateEngine() =
    {
      new ClockEngine(this)
    }


  val batteryReceiver = new BatteryReceiver()



  class Runnable(engine: ClockEngine) extends java.lang.Runnable
  {
    def run()
    {
      engine.drawFrame()
    }
  }


  class ClockEngine(service: DigitalClockWallpaper) extends Engine
  {
    private val paint = new graphics.Paint()
    var offset: (Float, Float) = (0, 0)
    val startTime = os.SystemClock.elapsedRealtime()

    var center = (0, 0)
    var screenSize = (0, 0)

    var baseFontSize = 0

    private val drawClock = new Runnable(this)

    var visible: Boolean = false

    def Initialize()
    {
      val p = this.paint
      p.setColor(0xffffffff)
      p.setAntiAlias(true)
      p.setStrokeWidth(2)
      p.setStrokeCap(graphics.Paint.Cap.ROUND)
      p.setStyle(graphics.Paint.Style.STROKE)

      // font 描画設定
      p.setTextAlign(graphics.Paint.Align.CENTER)
      p.setTextSize(64)
      p.setLinearText(true)

      // フォント設定
      p.setTypeface(graphics.Typeface.MONOSPACE)
    }


    def updateFontSize()
    {
      val targetsize = this.screenSize._1 / 2
      val threshold = 16

      def measure(size:Int) = 
        {
          this.paint.setTextSize(size)
          this.paint.measureText("00:00")
        }
      
      def calcFontSize(size:Int): Int =
        {
          val w = measure(size)
          val diff = targetsize-w
          val step = (diff/8).toInt

          util.Log.v(TAG, size.toString)
          
          if (diff > 0 && diff < threshold)
            {
              size
            }
          else if (diff > 0)
            {
              calcFontSize(size+step)
            }
          else
            {
              calcFontSize(size-step)
            }
        }
      
      if (targetsize != 0)
        {
          this.baseFontSize = calcFontSize(32)
        }
    }

    override def onCreate(surfaceHolder: view.SurfaceHolder)
    {
      super.onCreate(surfaceHolder)
    }

    override def onDestroy()
    {
      super.onDestroy()
      service.handler.removeCallbacks(this.drawClock)
    }

    override def onVisibilityChanged(visible: Boolean)
    {
      this.visible = visible
      
      if (visible)
        {
          this.drawFrame()

          val filter = new content.IntentFilter()
          filter addAction content.Intent.ACTION_BATTERY_CHANGED
          service.registerReceiver(batteryReceiver, filter)
        }
      else
        {
          service.handler.removeCallbacks(this.drawClock)
          service.unregisterReceiver(batteryReceiver)
        }
    }

    override def onSurfaceChanged(surfaceHolder: view.SurfaceHolder,
                                  format: Int,
                                  width: Int,
                                  height: Int)
    {
      super.onSurfaceChanged(surfaceHolder, format, width, height)
      this.center = ((width.toFloat/2.0).toInt, (height.toFloat/2.0).toInt)
      this.screenSize = (width, height)
      this.updateFontSize()
      this.drawFrame()
    }

    override def onSurfaceCreated(surfaceHolder: view.SurfaceHolder)
    {
      super.onSurfaceCreated(surfaceHolder)
    }

    override def onSurfaceDestroyed(surfaceHolder: view.SurfaceHolder)
    {
      super.onSurfaceDestroyed(surfaceHolder)
      this.visible = false
      service.handler.removeCallbacks(this.drawClock)
    }

    override def onOffsetsChanged(xoffset: Float,
                                  yoffset: Float,
                                  xstep: Float,
                                  ystep: Float,
                                  xpixels: Int,
                                  ypixels: Int)
    {
      this.offset = (xoffset, yoffset)
      this.drawFrame()
    }


    def drawFrame()
    {
      val holder = this.getSurfaceHolder()
      var c: graphics.Canvas = holder.lockCanvas()

      try
      {
        if (c != null)
          {
            this.drawClock(c)
          }
      }
      finally
      {
        if (c != null)
          {
            holder.unlockCanvasAndPost(c)
          }
      }

      service.handler.removeCallbacks(this.drawClock)
      if (this.visible)
        {
          service.handler.postDelayed(this.drawClock, 1000/25)
        }
    }


    def drawClock(c: graphics.Canvas)
    {
      this.drawClock(c, 0, 0)
    }

    def drawClock(c: graphics.Canvas, basex: Int, basey: Int)
    {
      val p = this.paint
      val currentTime = java.util.Calendar.getInstance

      def createTimeText() = String.format(
        if (currentTime.get(java.util.Calendar.SECOND) % 2 == 0)
          "%1$tH:%1$tM"
        else
          "%1$tH %1$tM"
        , currentTime)
      def createDateText() = String.format("%1$tY/%1$tm/%1$td(%1$ta)", currentTime)
      def createSecText() = String.format("%1$tS", currentTime)

      val hourmin = createTimeText()

      def getTimeTextWidth(): Float =
        {
          fontSettings(this.baseFontSize, graphics.Paint.Align.CENTER)
          p measureText hourmin
        }
      
      def fontSettings(size:Int, align:graphics.Paint.Align)
      {
        p setColor 0xffffffff
        p setAntiAlias true
        p setStrokeWidth 2
        p setStrokeCap graphics.Paint.Cap.ROUND
        p setStyle graphics.Paint.Style.STROKE
        
        // font 描画設定
        p setTextAlign align
        p setTextSize size
        p setLinearText true

        // フォント設定
        // p.setTypeface(graphics.Typeface.MONOSPACE)
      }

      def drawTime()
      {
        // val posy = this.baseFontSize * 2 / 3
        val posy = 0
        fontSettings(this.baseFontSize, graphics.Paint.Align.CENTER)
        val text = hourmin

        c.drawText(text, basex+0, basey+posy, p)
      }

      def drawDate()
      {
        val fontsize = this.baseFontSize / 4
        val timetextwidth = getTimeTextWidth
        val posx = -timetextwidth/2
        val posy = -(this.baseFontSize * 2 / 3 + fontsize / 2 + fontsize/2)
        val text = createDateText()
        
        fontSettings(fontsize, graphics.Paint.Align.LEFT)

        c.drawText(text, basex+posx, basey+posy, p)
      }


      def drawBatteryStatus()
      {
        val fontsize = this.baseFontSize / 4
        val timetextwidth = getTimeTextWidth
        val posx = timetextwidth/2
        val posy = (this.baseFontSize / 3 + fontsize / 2 + fontsize/3)
        val text = "%s % 3d%%".format(service.batteryReceiver.getBatteryStatus,
                                      service.batteryReceiver.getBatteryLevel)
        
        fontSettings(fontsize, graphics.Paint.Align.RIGHT)

        c.drawText(text, basex+posx, basey+posy, p)
      }

      c.save()
      c.drawColor(0xff000000)
      c.translate(this.center._1, this.center._2)
      drawTime()
      drawDate()
      drawBatteryStatus()
      c.restore()
    }


    def drawLine(c: graphics.Canvas,
                 x1: Int,
                 y1: Int,
                 x2: Int,
                 y2: Int)
    {
      c.drawLine(x1, y1, x2, y2, this.paint)
    }

    Initialize()
  }


}

