package com.devbrackets.android.playlistcoredemo.ui.activity

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.app.MediaRouteButton
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.listener.PlaylistListener
import com.devbrackets.android.playlistcore.listener.ProgressListener
import com.devbrackets.android.playlistcoredemo.App
import com.devbrackets.android.playlistcoredemo.R
import com.devbrackets.android.playlistcoredemo.data.MediaItem
import com.devbrackets.android.playlistcoredemo.data.Samples
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager
import com.google.android.gms.cast.framework.CastButtonFactory
import java.util.*

/**
 * An example activity to show how to implement and audio UI
 * that interacts with the [com.devbrackets.android.playlistcore.service.BasePlaylistService]
 * and [com.devbrackets.android.playlistcore.manager.ListPlaylistManager]
 * classes.
 */
class AudioPlayerActivity : AppCompatActivity(), PlaylistListener<MediaItem>, ProgressListener {
  private var loadingBar: ProgressBar? = null
  private var artworkView: ImageView? = null
  private var currentPositionView: TextView? = null
  private var durationView: TextView? = null
  private var seekBar: SeekBar? = null
  private var shouldSetDuration = false
  private var userInteracting = false
  private var titleTextView: TextView? = null
  private var subtitleTextView: TextView? = null
  private var descriptionTextView: TextView? = null
  private var previousButton: ImageButton? = null
  private var playPauseButton: ImageButton? = null
  private var nextButton: ImageButton? = null
  private var castButton: MediaRouteButton? = null
  protected val playlistManager: PlaylistManager by lazy {
    (applicationContext as App).playlistManager
  }
  private var selectedPosition = 0
  private var glide: RequestManager? = null


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.audio_player_activity)
    retrieveExtras()
    init()
  }

  override fun onPause() {
    super.onPause()
    playlistManager.unRegisterPlaylistListener(this)
    playlistManager.unRegisterProgressListener(this)
  }

  override fun onResume() {
    super.onResume()
    playlistManager.registerPlaylistListener(this)
    playlistManager.registerProgressListener(this)

    //Makes sure to retrieve the current playback information
    updateCurrentPlaybackInformation()
  }

  override fun onPlaylistItemChanged(currentItem: MediaItem?, hasNext: Boolean, hasPrevious: Boolean): Boolean {
    shouldSetDuration = true

    //Updates the button states
    nextButton!!.isEnabled = hasNext
    previousButton!!.isEnabled = hasPrevious

    //Loads the new image
    if (currentItem != null) {
      artworkView?.let { view ->
        glide?.load(currentItem.artworkUrl)?.into(view)
      }
    }

    // Updates the title, subtitle, and description
    titleTextView!!.text = currentItem?.title.orEmpty()
    subtitleTextView!!.text = currentItem?.album.orEmpty()
    descriptionTextView!!.text = currentItem?.artist.orEmpty()
    return true
  }

  override fun onPlaybackStateChanged(playbackState: PlaybackState): Boolean {
    when (playbackState) {
      PlaybackState.STOPPED -> finish()
      PlaybackState.RETRIEVING, PlaybackState.PREPARING, PlaybackState.SEEKING -> restartLoading()
      PlaybackState.PLAYING -> doneLoading(true)
      PlaybackState.PAUSED -> doneLoading(false)
      else -> {}
    }
    return true
  }

  override fun onProgressUpdated(progress: MediaProgress): Boolean {
    if (shouldSetDuration && progress.duration > 0) {
      shouldSetDuration = false
      setDuration(progress.duration)
    }
    if (!userInteracting) {
      seekBar!!.secondaryProgress = (progress.duration * progress.bufferPercentFloat).toInt()
      seekBar!!.progress = progress.position.toInt()
      currentPositionView!!.text = formatMs(progress.position)
    }
    return true
  }

  /**
   * Makes sure to update the UI to the current playback item.
   */
  private fun updateCurrentPlaybackInformation() {
    val itemChange = playlistManager.currentItemChange
    if (itemChange != null) {
      onPlaylistItemChanged(itemChange.currentItem, itemChange.hasNext, itemChange.hasPrevious)
    }
    val currentPlaybackState = playlistManager.currentPlaybackState
    if (currentPlaybackState !== PlaybackState.STOPPED) {
      onPlaybackStateChanged(currentPlaybackState)
    }
    val mediaProgress = playlistManager.currentProgress
    mediaProgress?.let { onProgressUpdated(it) }
  }

  /**
   * Retrieves the extra associated with the selected playlist index
   * so that we can start playing the correct item.
   */
  private fun retrieveExtras() {
    val extras = intent.extras
    selectedPosition = extras!!.getInt(EXTRA_INDEX, 0)
  }

  /**
   * Performs the initialization of the views and any other
   * general setup
   */
  private fun init() {
    retrieveViews()
    setupListeners()
    glide = Glide.with(this)
    CastButtonFactory.setUpMediaRouteButton(applicationContext, castButton!!)
    val generatedPlaylist = setupPlaylistManager()
    startPlayback(generatedPlaylist)
  }

  /**
   * Called when we receive a notification that the current item is
   * done loading.  This will then update the view visibilities and
   * states accordingly.
   *
   * @param isPlaying True if the audio item is currently playing
   */
  private fun doneLoading(isPlaying: Boolean) {
    loadCompleted()
    updatePlayPauseImage(isPlaying)
  }

  /**
   * Updates the Play/Pause image to represent the correct playback state
   *
   * @param isPlaying True if the audio item is currently playing
   */
  private fun updatePlayPauseImage(isPlaying: Boolean) {
    val resId = if (isPlaying) R.drawable.ic_pause_black_24dp else R.drawable.ic_play_black_24dp
    playPauseButton!!.setImageResource(resId)
  }

  /**
   * Used to inform the controls to finalize their setup.  This
   * means replacing the loading animation with the PlayPause button
   */
  fun loadCompleted() {
    playPauseButton!!.visibility = View.VISIBLE
    previousButton!!.visibility = View.VISIBLE
    nextButton!!.visibility = View.VISIBLE
    loadingBar!!.visibility = View.INVISIBLE
  }

  /**
   * Used to inform the controls to return to the loading stage.
   * This is the opposite of [.loadCompleted]
   */
  fun restartLoading() {
    playPauseButton!!.visibility = View.INVISIBLE
    previousButton!!.visibility = View.INVISIBLE
    nextButton!!.visibility = View.INVISIBLE
    loadingBar!!.visibility = View.VISIBLE
  }

  /**
   * Sets the [.seekBar]s max and updates the duration text
   *
   * @param duration The duration of the media item in milliseconds
   */
  private fun setDuration(duration: Long) {
    seekBar!!.max = duration.toInt()
    durationView!!.text = formatMs(duration)
  }

  /**
   * Retrieves the playlist instance and performs any generation
   * of content if it hasn't already been performed.
   *
   * @return True if the content was generated
   */
  private fun setupPlaylistManager(): Boolean {
    //There is nothing to do if the currently playing values are the same
    if (playlistManager.id == PLAYLIST_ID.toLong()) {
      return false
    }

    val mediaItems: MutableList<MediaItem> = LinkedList()
    for (sample in Samples.audio) {
      val mediaItem = MediaItem(sample, true)
      mediaItems.add(mediaItem)
    }

    playlistManager.setParameters(mediaItems, selectedPosition)
    playlistManager.id = PLAYLIST_ID.toLong()
    return true
  }

  /**
   * Populates the class variables with the views created from the
   * xml layout file.
   */
  private fun retrieveViews() {
    loadingBar = findViewById(R.id.audio_player_loading)
    artworkView = findViewById(R.id.audio_player_image)
    currentPositionView = findViewById(R.id.audio_player_position)
    durationView = findViewById(R.id.audio_player_duration)
    seekBar = findViewById(R.id.audio_player_seek)
    titleTextView = findViewById(R.id.title_text_view)
    subtitleTextView = findViewById(R.id.subtitle_text_view)
    descriptionTextView = findViewById(R.id.description_text_view)
    previousButton = findViewById(R.id.audio_player_previous)
    playPauseButton = findViewById(R.id.audio_player_play_pause)
    nextButton = findViewById(R.id.audio_player_next)
    castButton = findViewById(R.id.media_route_button)
  }

  /**
   * Links the SeekBarChanged to the [.seekBar] and
   * onClickListeners to the media buttons that call the appropriate
   * invoke methods in the [.playlistManager]
   */
  private fun setupListeners() {
    seekBar!!.setOnSeekBarChangeListener(SeekBarChanged())
    previousButton!!.setOnClickListener { playlistManager.invokePrevious() }
    playPauseButton!!.setOnClickListener { playlistManager.invokePausePlay() }
    nextButton!!.setOnClickListener { playlistManager.invokeNext() }
  }

  /**
   * Starts the audio playback if necessary.
   *
   * @param forceStart True if the audio should be started from the beginning even if it is currently playing
   */
  private fun startPlayback(forceStart: Boolean) {
    //If we are changing audio files, or we haven't played before then start the playback
    if (forceStart || playlistManager.currentPosition != selectedPosition) {
      playlistManager.currentPosition = selectedPosition
      playlistManager.play(0, false)
    }
  }

  /**
   * Listens to the seek bar change events and correctly handles the changes
   */
  private inner class SeekBarChanged : OnSeekBarChangeListener {
    private var seekPosition = -1
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
      if (!fromUser) {
        return
      }
      seekPosition = progress
      currentPositionView!!.text = formatMs(progress.toLong())
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
      userInteracting = true
      seekPosition = seekBar.progress
      playlistManager.invokeSeekStarted()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
      userInteracting = false
      playlistManager.invokeSeekEnded(seekPosition.toLong())
      seekPosition = -1
    }
  }

  companion object {
    const val EXTRA_INDEX = "EXTRA_INDEX"
    const val PLAYLIST_ID = 4 //Arbitrary, for the example
    private val formatBuilder = StringBuilder()
    private val formatter = Formatter(formatBuilder, Locale.getDefault())

    /**
     * Formats the specified milliseconds to a human readable format
     * in the form of (Hours : Minutes : Seconds).  If the specified
     * milliseconds is less than 0 the resulting format will be
     * "--:--" to represent an unknown time
     *
     * @param milliseconds The time in milliseconds to format
     * @return The human readable time
     */
    fun formatMs(milliseconds: Long): String {
      if (milliseconds < 0) {
        return "--:--"
      }
      val seconds = milliseconds % DateUtils.MINUTE_IN_MILLIS / DateUtils.SECOND_IN_MILLIS
      val minutes = milliseconds % DateUtils.HOUR_IN_MILLIS / DateUtils.MINUTE_IN_MILLIS
      val hours = milliseconds % DateUtils.DAY_IN_MILLIS / DateUtils.HOUR_IN_MILLIS
      formatBuilder.setLength(0)
      return if (hours > 0) {
        formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
      } else formatter.format("%02d:%02d", minutes, seconds).toString()
    }
  }
}