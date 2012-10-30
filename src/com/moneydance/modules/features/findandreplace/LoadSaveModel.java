package com.moneydance.modules.features.findandreplace;

import com.moneydance.apps.md.controller.Util;
import com.moneydance.apps.md.model.Account;
import com.moneydance.apps.md.model.RootAccount;
import com.moneydance.apps.md.model.TxnTag;
import com.moneydance.apps.md.model.TxnTagSet;
import com.moneydance.apps.md.view.gui.TagLogic;
import com.moneydance.util.StreamTable;
import com.moneydance.util.StringEncodingException;
import com.moneydance.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LoadSaveModel
{
    private static final String SAVE_KEY_NAME = "mennesoft.far";
    private static final String SEARCH_LIST = "searches";
    private static final String SEARCH_NAME = "nm";
    private static final String FIND_COMBINE = "f.cmb";
    private static final String FIND_INC_XFER = "f.xfer";
    private static final String FIND_ACCOUNTS = "f.accts";
    private static final String FIND_CATEGORIES = "f.cats";
    private static final String FIND_AMT_MIN = "f.amt.lo";
    private static final String FIND_AMT_MAX = "f.amt.hi";
    private static final String FIND_DATE_OPTION = "f.date.opt";
    private static final String FIND_DATE_MIN = "f.date.lo";
    private static final String FIND_DATE_MAX = "f.date.hi";
    private static final String FIND_USE_TAX_DATE = "f.taxDt";
    private static final String FIND_TEXT = "f.text.text";
    private static final String FIND_USE_DESC = "f.text.d";
    private static final String FIND_USE_MEMO = "f.text.m";
    private static final String FIND_USE_CHECK = "f.text.c";
    private static final String FIND_INC_SPLIT = "f.text.s";
    private static final String FIND_TAGS = "f.tags";
    private static final String FIND_ALLOW_CLEARED = "f.clr.clr";
    private static final String FIND_ALLOW_RECONCILE = "f.clr.rec";
    private static final String FIND_ALLOW_UNCLEARED = "f.clr.unc";
    private static final String REPL_CATEGORY = "r.cat";
    private static final String REPL_AMOUNT = "r.amt";
    private static final String REPL_DESC = "r.desc";
    private static final String REPL_DESC_FOUND_ONLY = "r.desc.fo";
    private static final String REPL_MEMO = "r.memo";
    private static final String REPL_MEMO_FOUND_ONLY = "r.memo.fo";
    private static final String REPL_CHECK = "r.check";
    private static final String REPL_CHECK_FOUND_ONLY = "r.check.fo";
    private static final String REPL_TAGS_ADD = "r.tags.add";
    private static final String REPL_TAGS_REMOVE = "r.tags.remove";
    private static final String REPL_TAGS_REPLACE = "r.tags.replace";

    private final RootAccount _rootAccount;
    private final FarController _controller;

    /** The key is the user-supplied name (non-blank, unique), value are the settings. */
    private final Map<String, String> _savedSearches = new TreeMap<String, String>();
    
    private String _currentSearchName = N12EFindAndReplace.EMPTY;
    
    LoadSaveModel(final RootAccount rootAccount, final FarController controller)
    {
        _rootAccount = rootAccount;
        _controller = controller;
        loadSavedSearchList();
    }
    
    Collection<String> getSavedSearchNames()
    {
        return Collections.unmodifiableCollection(_savedSearches.keySet());
    }
    
    String getCurrentSearchName()
    {
        return _currentSearchName;
    }

    public boolean isValidName(final String candidateName)
    {
        if (StringUtils.isBlank(candidateName)) return false;
        for (String currentName : _savedSearches.keySet())
        {
            if (currentName.equals(candidateName)) return false;
        }
        return true;
    }

    public void saveCurrentSearch(final String name)
    {
        // store any user edits to ensure they are picked up
        _controller.saveUserEdits();
        StreamTable streamTable = new StreamTable();
        streamTable.put(SEARCH_NAME, name);
        saveFindCriteria(streamTable);
        saveReplaceInfo(streamTable);
        _savedSearches.put(name, streamTable.writeToString());
        _currentSearchName = name;
        saveSavedSearchList();
    }
    
    public void loadSearchSettings(final String name)
    {
        final String settings = _savedSearches.get(name);
        if (StringUtils.isBlank(settings)) return;
        final StreamTable streamTable = new StreamTable();
        try
        {
            streamTable.readFrom(settings);
            _controller.setAllowEvents(false);
            _controller.reset();
            final HashSet<String> keySet = new HashSet<String>(
                    Arrays.asList(streamTable.getKeyArray()));
            loadFindCriteria(streamTable, keySet);
            loadReplaceInfo(streamTable, keySet);
            _controller.setAllowEvents(true);
            _currentSearchName = name;
        }
        catch (StringEncodingException e)
        {
            System.err.println("findandreplace: Error loading saved settings: "+e.getMessage());
        }
    }

    public void deleteSavedSearch(final String searchName)
    {
        if (searchName == null) return;
        if (!_savedSearches.containsKey(searchName)) return;
        _savedSearches.remove(searchName);
        if (searchName.equals(_currentSearchName))
        {
            _currentSearchName = N12EFindAndReplace.EMPTY;
        }
        saveSavedSearchList();
    }

    private void loadSavedSearchList()
    {
        if (_rootAccount == null) return;
        _savedSearches.clear();
        String settings = _rootAccount.getParameter(SAVE_KEY_NAME, N12EFindAndReplace.EMPTY);
        if (StringUtils.isBlank(settings)) return;
        StreamTable streamTable = new StreamTable();
        try
        {
            streamTable.readFrom(settings);
            final String[] searchList = streamTable.getStrList(SEARCH_LIST);
            for (String searchSetting : searchList)
            {
                loadSavedSearchInfo(searchSetting);
            }
        }
        catch (StringEncodingException error)
        {
            System.err.println("findandreplace: Error retrieving settings: " + error.getMessage());
        }
    }

    private void loadSavedSearchInfo(String searchSetting) throws StringEncodingException
    {
        if (StringUtils.isBlank(searchSetting)) return;
        StreamTable streamTable = new StreamTable();
        streamTable.readFrom(searchSetting);
        String name = streamTable.getStr(SEARCH_NAME, N12EFindAndReplace.EMPTY);
        if (StringUtils.isBlank(name)) return;
        _savedSearches.put(name, searchSetting);
    }

    private void saveSavedSearchList()
    {
        StreamTable streamTable = new StreamTable();
        final Collection<String> values = _savedSearches.values();
        final String[] settingsList = values.toArray(new String[values.size()]);
        streamTable.setField(SEARCH_LIST, settingsList);
        _rootAccount.setParameter(SAVE_KEY_NAME, streamTable.writeToString());
        // notify user they changed something
        _rootAccount.accountModified(_rootAccount);
    }

    private void saveFindCriteria(final StreamTable streamTable)
    {
        streamTable.put(FIND_COMBINE, _controller.getFilterCombineOr());
        streamTable.put(FIND_INC_XFER, _controller.getIncludeTransfers());
        if (_controller.getUseAccountFilter())
        {
            streamTable.put(FIND_ACCOUNTS, _controller.getAccountListSave());
        }
        if (_controller.getUseCategoryFilter())
        {
            streamTable.put(FIND_CATEGORIES, _controller.getCategoryListSave());
        }
        if (_controller.getUseAmountFilter())
        {
            streamTable.put(FIND_AMT_MIN, _controller.getAmountMinimum());
            streamTable.put(FIND_AMT_MAX, _controller.getAmountMaximum());
        }
        if (_controller.getUseDateFilter())
        {
            streamTable.put(FIND_DATE_OPTION, _controller.getDateRangeKey());
            streamTable.put(FIND_DATE_MIN, _controller.getDateMinimum());
            streamTable.put(FIND_DATE_MAX, _controller.getDateMaximum());
            streamTable.put(FIND_USE_TAX_DATE, _controller.getUseTaxDate());
        }
        if (_controller.getUseFreeTextFilter())
        {
            streamTable.put(FIND_TEXT, _controller.getFreeTextMatch());
            streamTable.put(FIND_USE_DESC, _controller.getFreeTextUseDescription());
            streamTable.put(FIND_USE_MEMO, _controller.getFreeTextUseMemo());
            streamTable.put(FIND_USE_CHECK, _controller.getFreeTextUseCheck());
            streamTable.put(FIND_INC_SPLIT, _controller.getFreeTextIncludeSplits());
        }
        if (_controller.getUseTagsFilter())
        {
            final TagPickerModel tagModel = _controller.getIncludedTagsModel();
            final TagLogic tagLogic = _controller.getRequireTagsFilter();
            streamTable.put(FIND_TAGS, saveTags(tagModel, tagLogic));
        }
        if (_controller.getUseClearedFilter())
        {
            streamTable.put(FIND_ALLOW_CLEARED, _controller.getAllowCleared());
            streamTable.put(FIND_ALLOW_RECONCILE, _controller.getAllowReconciling());
            streamTable.put(FIND_ALLOW_UNCLEARED, _controller.getAllowUncleared());
        }
    }
    
    private void loadFindCriteria(final StreamTable streamTable, final Set<String> keySet)
    {
        _controller.setFilterCombineOr(streamTable.getBoolean(FIND_COMBINE, false));
        _controller.setIncludeTransfers(streamTable.getBoolean(FIND_INC_XFER, false));
        if (keySet.contains(FIND_ACCOUNTS))
        {
            _controller.setUseAccountFilter(true);
            _controller.setAccountListSave(streamTable.getStr(FIND_ACCOUNTS, N12EFindAndReplace.EMPTY));
        }
        if (keySet.contains(FIND_CATEGORIES))
        {
            _controller.setUseCategoryFilter(true);
            _controller.setCategoryListSave(streamTable.getStr(FIND_CATEGORIES, N12EFindAndReplace.EMPTY));
        }
        if (keySet.contains(FIND_AMT_MIN))
        {
            _controller.setUseAmountFilter(true);
            _controller.setAmountRange(streamTable.getLong(FIND_AMT_MIN, 0),
                                       streamTable.getLong(FIND_AMT_MAX, 1));
        }
        if (keySet.contains(FIND_DATE_MIN))
        {
            int today = Util.getStrippedDateInt();
            _controller.setUseDateFilter(true);
            String dateRangeKey = streamTable.getStr(FIND_DATE_OPTION, N12EFindAndReplace.EMPTY);
            // set the date range and use tax date, but could get overridden by a pre-defined date range
            _controller.setDateRange(streamTable.getInt(FIND_DATE_MIN, today),
                                     streamTable.getInt(FIND_DATE_MAX, today),
                                     streamTable.getBoolean(FIND_USE_TAX_DATE, false));
            if (StringUtils.isBlank(dateRangeKey) ||
                    DateRangeOption.DR_CUSTOM_DATE.getResourceKey().equals(dateRangeKey))
            {
                _controller.setDateRangeKey(DateRangeOption.DR_CUSTOM_DATE.getResourceKey());
            }
            else
            {
                // predefined date range - override the dates with newly calculated ones
                _controller.setDateRangeKey(dateRangeKey);
            }
        }
        if (keySet.contains(FIND_TEXT))
        {
            _controller.setUseFreeTextFilter(true);
            _controller.setFreeTextMatch(streamTable.getStr(FIND_TEXT, N12EFindAndReplace.EMPTY));
            _controller.setFreeTextUseDescription(streamTable.getBoolean(FIND_USE_DESC, true));
            _controller.setFreeTextUseMemo(streamTable.getBoolean(FIND_USE_MEMO, true));
            _controller.setFreeTextUseCheck(streamTable.getBoolean(FIND_USE_CHECK, true));
            _controller.setFreeTextIncludeSplits(streamTable.getBoolean(FIND_INC_SPLIT, true));
        }
        if (keySet.contains(FIND_TAGS))
        {
            _controller.setUseTagsFilter(true);
            final String settings = streamTable.getStr(FIND_TAGS, N12EFindAndReplace.EMPTY);
            if (!StringUtils.isBlank(settings))
            {
                final TagsInfo info = loadTagsFromSettings(settings);
                final TagPickerModel tagModel = _controller.getIncludedTagsModel();
                tagModel.setSelectedTags(info.tags);
                _controller.setTagsFilterLogic(info.logic);
            }
        }
        if (keySet.contains(FIND_ALLOW_CLEARED))
        {
            _controller.setUseClearedFilter(true);
            _controller.setAllowCleared(streamTable.getBoolean(FIND_ALLOW_CLEARED, true));
            _controller.setAllowReconciling(streamTable.getBoolean(FIND_ALLOW_RECONCILE, true));
            _controller.setAllowUncleared(streamTable.getBoolean(FIND_ALLOW_UNCLEARED, true));
        }
    }

    private void saveReplaceInfo(final StreamTable streamTable)
    {
        if (_controller.getReplaceCategory())
        {

            final Account category = _controller.getReplacementCategory();
            if (category != null)
            {
                streamTable.put(REPL_CATEGORY, category.getAccountNum());
            }
        }
        if (_controller.getReplaceAmount())
        {
            streamTable.put(REPL_AMOUNT, _controller.getReplacementAmount());
        }
        if (_controller.getReplaceDescription())
        {
            streamTable.put(REPL_DESC, _controller.getReplacementDescription());
            streamTable.put(REPL_DESC_FOUND_ONLY, _controller.getReplaceFoundDescriptionOnly());
        }
        if (_controller.getReplaceMemo())
        {
            streamTable.put(REPL_MEMO, _controller.getReplacementMemo());
            streamTable.put(REPL_MEMO_FOUND_ONLY, _controller.getReplaceFoundMemoOnly());
        }
        if (_controller.getReplaceCheck())
        {
            streamTable.put(REPL_CHECK, _controller.getReplacementCheck());
            streamTable.put(REPL_CHECK_FOUND_ONLY, _controller.getReplaceFoundCheckOnly());
        }
        if (_controller.getReplaceTags())
        {
            ReplaceTagCommandType commandType = _controller.getReplaceTagType();
            if (commandType != null)
            {
                switch (commandType)
                {
                    case ADD:
                    {
                        streamTable.put(REPL_TAGS_ADD, 
                                        saveTags(_controller.getReplaceAddTagsModel(), null));
                        break;
                    }
                    case REMOVE:
                    {
                        streamTable.put(REPL_TAGS_REMOVE,
                                        saveTags(_controller.getReplaceRemoveTagsModel(), null));
                        break;
                    }
                    case REPLACE:
                    {
                        streamTable.put(REPL_TAGS_REPLACE,
                                        saveTags(_controller.getReplaceReplaceTagsModel(), null));
                        break;
                    }
                }
            }
        }
    }
    
    private void loadReplaceInfo(final StreamTable streamTable, final Set<String> keySet)
    {
        if (keySet.contains(REPL_CATEGORY))
        {
            int accountId = streamTable.getInt(REPL_CATEGORY, -1);
            Account account = null;
            if (accountId >= 0)
            {
                account = _rootAccount.getAccountById(accountId);
            }
            if (account != null)
            {
                _controller.setReplaceCategory(true);
                _controller.setReplacementCategory(account);
            }
        }
        if (keySet.contains(REPL_AMOUNT))
        {
            _controller.setReplaceAmount(true);
            _controller.setReplacementAmount(streamTable.getLong(REPL_AMOUNT, 0));
        }
        if (keySet.contains(REPL_DESC))
        {
            _controller.setReplaceDescription(true);
            _controller.setReplacementDescription(
                    streamTable.getStr(REPL_DESC, N12EFindAndReplace.EMPTY));
            _controller.setReplaceFoundDescriptionOnly(
                    streamTable.getBoolean(REPL_DESC_FOUND_ONLY, false));
        }
        if (keySet.contains(REPL_MEMO))
        {
            _controller.setReplaceMemo(true);
            _controller.setReplacementMemo(
                    streamTable.getStr(REPL_MEMO, N12EFindAndReplace.EMPTY));
            _controller.setReplaceFoundMemoOnly(
                    streamTable.getBoolean(REPL_MEMO_FOUND_ONLY, false));
        }
        if (keySet.contains(REPL_CHECK))
        {
            _controller.setReplaceCheck(true);
            _controller.setReplacementCheck(
                    streamTable.getStr(REPL_CHECK, N12EFindAndReplace.EMPTY));
            _controller.setReplaceFoundCheckOnly(
                    streamTable.getBoolean(REPL_CHECK_FOUND_ONLY, false));
        }
        if (keySet.contains(REPL_TAGS_ADD))
        {
            _controller.setReplaceTags(true);
            _controller.setReplaceTagType(ReplaceTagCommandType.ADD);
            final String settings = streamTable.getStr(REPL_TAGS_ADD, N12EFindAndReplace.EMPTY);
            if (!StringUtils.isBlank(settings))
            {
                final TagsInfo info = loadTagsFromSettings(settings);
                final TagPickerModel tagModel = _controller.getReplaceAddTagsModel();
                tagModel.setSelectedTags(info.tags);
            }
        }
        if (keySet.contains(REPL_TAGS_REMOVE))
        {
            _controller.setReplaceTags(true);
            _controller.setReplaceTagType(ReplaceTagCommandType.REMOVE);
            final String settings = streamTable.getStr(REPL_TAGS_REMOVE, N12EFindAndReplace.EMPTY);
            if (!StringUtils.isBlank(settings))
            {
                final TagsInfo info = loadTagsFromSettings(settings);
                final TagPickerModel tagModel = _controller.getReplaceRemoveTagsModel();
                tagModel.setSelectedTags(info.tags);
            }
        }
        if (keySet.contains(REPL_TAGS_REPLACE))
        {
            _controller.setReplaceTags(true);
            _controller.setReplaceTagType(ReplaceTagCommandType.REPLACE);
            final String settings = streamTable.getStr(REPL_TAGS_REPLACE, N12EFindAndReplace.EMPTY);
            if (!StringUtils.isBlank(settings))
            {
                final TagsInfo info = loadTagsFromSettings(settings);
                final TagPickerModel tagModel = _controller.getReplaceReplaceTagsModel();
                tagModel.setSelectedTags(info.tags);
            }
        }
    }
    
    private String saveTags(final TagPickerModel tagModel, final TagLogic tagLogic)
    {
        // store a '|' delimited list of tags
        final String tagList = TxnTagSet.getIDStringForTags(tagModel.getSelectedTags());
        final StringBuilder sb = new StringBuilder();
        sb.append(tagList);
        if (tagLogic != null)
        {
            sb.append('+');
            sb.append(tagLogic.getConfigKey());
        }
        return sb.toString();
    }
    
    private TagsInfo loadTagsFromSettings(final String settings)
    {
        final int delimIndex = settings.lastIndexOf('+');
        TagLogic tagLogic = TagLogic.OR;
        String tagStr = settings;
        if (delimIndex >= 0)
        {
            String logic = settings.substring(delimIndex + 1);
            tagLogic = TagLogic.fromString(logic);
            tagStr = settings.substring(0, delimIndex);
        }
        return new TagsInfo(tagStr, tagLogic);
    }
    
    private class TagsInfo
    {
        private final TxnTag[] tags;
        private final TagLogic logic;
        
        TagsInfo(final String tagsStr, final TagLogic tagLogic)
        {
            tags = _rootAccount.getTxnTagSet().getTagsForIDString(tagsStr);
            logic = tagLogic;
        }
    }
}