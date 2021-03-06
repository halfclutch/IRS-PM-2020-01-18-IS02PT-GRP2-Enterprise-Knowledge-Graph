import React from 'react';
import { fade, makeStyles } from '@material-ui/core/styles';
import TextField from '@material-ui/core/TextField';
import InputAdornment from '@material-ui/core/InputAdornment';
import Grid from '@material-ui/core/Grid';
import Autocomplete from '@material-ui/lab/Autocomplete';
import SearchIcon from '@material-ui/icons/Search';
import SearchService from '../../services/SearchService';

const backend = new SearchService();

const useStyles = makeStyles(theme => ({
  root: {
    marginLeft: theme.spacing(2)
  },
  searchIcon: {
    margin: theme.spacing(0,1)
  },
  searchInput: {
    color: 'inherit',
  },
  searchTextField: {
    width: '100%',
    [theme.breakpoints.up('sm')]: {
      width: 300,
    },
    [theme.breakpoints.up('md')]: {
      width: 600,
    },
    [theme.breakpoints.up('lg')]: {
      width: 900,
    },
    backgroundColor: fade(theme.palette.common.white, 0.05),
    '&:hover': {
      backgroundColor: fade(theme.palette.common.white, 0.1),
    },
    borderRadius: theme.shape.borderRadius
  }
}));

export default function SearchBar({onSearchResult, onClearResult}) {
  const classes = useStyles();
  const inputRef = React.createRef();
  const [inputValue, setInputValue] = React.useState('');
  const [options, setOptions] = React.useState([]);

  const handleInputChange = (e, value) => {
    setInputValue(value);
    if(!value && onClearResult){
      onClearResult();
    }
  };
  const handleChange = (e, value) => {
    let enter = false;
    if(e.nativeEvent && e.nativeEvent.type === "keydown"){
      enter = true;
    }
    let input = inputRef.current;
    backend.searchAction({value}, enter || backend.getHistory().includes(value)).then(result => {
      if(result && result[0]){
        input.blur();
        //put successful search to history
        backend.addHistory(value);
        onSearchResult(result);
      }else if(enter) {
        onSearchResult([{
          name: "view",
          params: {
            description: [
              `Sorry, no result has been found for '${value}'.`,
              "Please try again with other inputs..."
            ]
          }
        }]);
        input.select();
      }else{
        input.focus();
      }
    });
  };

  React.useEffect(() => {
    let active = true;
    backend.searchSuggestionThrottled({ value: inputValue }).then(results => {
      if (active) {
        let opts = results || [];
        if(opts.length < 10){
          //add history search to suggestion
          let history = backend.getHistory(5, inputValue, opts);
          opts = [...history, ...opts];
        }
        setOptions(opts);
      }
    });
    return () => {
      active = false;
    };
  }, [inputValue]);

  return (
    <Autocomplete
      className = {classes.root}
      autoComplete
      freeSolo
      renderInput = {params => (
        <TextField {...params}
          classes={{
            root: classes.searchTextField
          }}
          variant="outlined"
          placeholder="Search…"
          InputProps={{
            ...params.InputProps,
            classes: {
              root: classes.searchInput
            },
            startAdornment: (
              <InputAdornment className={classes.searchIcon}>
                <SearchIcon />
              </InputAdornment>
            )
          }}
          inputRef = {inputRef}
        />
      )}
      options={options}
      getOptionLabel={option => (typeof option === 'string' ? option : option.description)}
      renderOption={option => {
        return (
          <Grid container alignItems="center">
            <Grid item xs>
              <span>{option}</span>
            </Grid>
          </Grid>
        );
      }}
      onInputChange = {handleInputChange}
      onChange = {handleChange}
      blurOnSelect = 'mouse'
    />
  );
}
