package com.aar.app.wordsearch.features.gamethemeselector;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.content.SharedPreferences;

import com.aar.app.wordsearch.data.network.responses.ThemeResponse;
import com.aar.app.wordsearch.data.room.GameThemeDataSource;
import com.aar.app.wordsearch.data.network.RetrofitClient;
import com.aar.app.wordsearch.data.network.responses.WordsUpdateResponse;
import com.aar.app.wordsearch.data.room.WordDataSource;
import com.aar.app.wordsearch.model.GameTheme;
import com.aar.app.wordsearch.model.Word;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class ThemeSelectorViewModel extends AndroidViewModel {

    private static final String PREF_NAME = "DataRev";
    private static final String KEY_DATA_REVISION = "data_revision";

    public enum ResponseType {
        NoUpdate,
        Updated
    }

    private SharedPreferences mPrefs;
    private GameThemeDataSource mGameThemeRepository;
    private WordDataSource mWordDataSource;

    private MutableLiveData<List<GameThemeItem>> mOnGameThemeLoaded;

    public ThemeSelectorViewModel(Application application,
                                  GameThemeDataSource gameThemeRepository,
                                  WordDataSource wordDataSource) {
        super(application);
        mGameThemeRepository = gameThemeRepository;
        mWordDataSource = wordDataSource;
        mOnGameThemeLoaded = new MutableLiveData<>();
        mPrefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @SuppressLint("CheckResult")
    public void loadThemes() {
        mGameThemeRepository.getThemesItem()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mOnGameThemeLoaded::setValue);
    }

    public Observable<ResponseType> updateData() {
        return RetrofitClient.getInstance().getWordDataService()
                .fetchWordsData(getLastDataRevision())
                .doOnNext(wordsUpdateResponse -> {
                    if (!wordsUpdateResponse.isUpdate()) return;

                    List<GameTheme> themes = new ArrayList<>();
                    List<Word> words = new ArrayList<>();

                    int idx = 0;
                    for (ThemeResponse themeResponse : wordsUpdateResponse.getData()) {
                        themes.add(new GameTheme(++idx, themeResponse.getName()));

                        for (String str : themeResponse.getWords()) {
                            words.add(new Word(0, idx, str));
                        }
                    }

                    mGameThemeRepository.deleteAll();
                    mWordDataSource.deleteAll();
                    mGameThemeRepository.insertAll(themes);
                    mWordDataSource.insertAll(words);
                    setLastDataRevision(wordsUpdateResponse.getRevision());
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap((Function<WordsUpdateResponse, Observable<ResponseType>>) wordsUpdateResponse -> {
                    if (wordsUpdateResponse.isUpdate())
                        return Observable.just(ResponseType.Updated);
                    return Observable.just(ResponseType.NoUpdate);
                });
    }

    public LiveData<List<GameThemeItem>> getOnGameThemeLoaded() {
        return mOnGameThemeLoaded;
    }

    public int getLastDataRevision() {
        return mPrefs.getInt(KEY_DATA_REVISION, 0);
    }

    private void setLastDataRevision(int revision) {
        mPrefs.edit()
                .putInt(KEY_DATA_REVISION, revision)
                .apply();
    }
}
